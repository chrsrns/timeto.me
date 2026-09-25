package me.timeto.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.KvDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A snooze deadline only outranks the timer's finish time while it still
 * belongs to the running interval, lies in the future, and that interval is
 * still expired.
 */
class SnoozeValidityTest {

    private suspend fun awaitSubscription() {
        withTimeout(3_000) {
            while (NotificationAlarm.flow.subscriptionCount.value == 0) delay(10)
        }
    }

    private suspend fun CoroutineScope.awaitAlarmInSeconds(): Int {
        val alarmsDeferred = async { NotificationAlarm.flow.first() }
        awaitSubscription()
        NotificationAlarm.rescheduleAll()
        val alarms = withTimeout(3_000) { alarmsDeferred.await() }
        return alarms.single { it.type is NotificationAlarm.Type.Alarm }.inSeconds
    }

    private suspend fun seedExpiredAlarmInterval() {
        insertActivitySq(id = 1, name = "Work", alarmMode = 1)
        insertIntervalSq(id = 1, time = time() - 700, activityId = 1, note = "deep work #t600")
        refreshCache()
    }

    private suspend fun setSnooze(intervalId: Int?, until: Int) {
        if (intervalId == null) {
            KvDb.KEY.ALARM_SNOOZE_INTERVAL_ID.delete()
        } else {
            KvDb.KEY.ALARM_SNOOZE_INTERVAL_ID.upsertInt(intervalId)
        }
        KvDb.KEY.ALARM_SNOOZE_UNTIL.upsertInt(until)
    }

    @Test
    fun validSnooze_outranksFinishTime() = runBlocking {
        initTestDb()
        seedExpiredAlarmInterval()
        setSnooze(intervalId = 1, until = time() + 120)

        val inSeconds = awaitAlarmInSeconds()

        assertTrue(inSeconds in 115..120, "expected the snooze deadline, got $inSeconds")
    }

    @Test
    fun intervalIdMismatch_ignoresSnooze() = runBlocking {
        initTestDb()
        seedExpiredAlarmInterval()
        setSnooze(intervalId = 2, until = time() + 120)

        // The timer expired 100s ago, so the finish time wins and the ring is due now.
        assertEquals(0, awaitAlarmInSeconds())
    }

    @Test
    fun pastDeadline_ignoresSnooze() = runBlocking {
        initTestDb()
        seedExpiredAlarmInterval()
        setSnooze(intervalId = 1, until = time() - 10)

        assertEquals(0, awaitAlarmInSeconds())
    }

    @Test
    fun missingSnoozeKeys_ignoresSnooze() = runBlocking {
        initTestDb()
        seedExpiredAlarmInterval()

        assertEquals(0, awaitAlarmInSeconds())
    }

    @Test
    fun timerExtendedPastExpiry_dropsSnooze() = runBlocking {
        initTestDb()
        seedExpiredAlarmInterval()
        setSnooze(intervalId = 1, until = time() + 120)

        // Restart the timer on the same interval: it is no longer expired.
        IntervalDb.selectLastOneOrNull()!!.updateTimer(timer = 3_600)

        val timerType = IntervalDb.selectLastOneOrNull()!!.buildTimerType() as IntervalDb.TimerType.Timer
        val expected = timerType.finishTime - time()

        val inSeconds = awaitAlarmInSeconds()

        assertTrue(
            inSeconds in (expected - 2)..(expected + 1),
            "expected the new finish time $expected, got $inSeconds",
        )
    }

    @Test
    fun runningTimerWithStaleSnooze_usesFinishTime() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work", alarmMode = 1)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1, note = "deep work #t3600")
        refreshCache()
        setSnooze(intervalId = 1, until = time() + 120)

        val inSeconds = awaitAlarmInSeconds()

        assertTrue(inSeconds > 3_000, "expected the finish time, got $inSeconds")
    }
}
