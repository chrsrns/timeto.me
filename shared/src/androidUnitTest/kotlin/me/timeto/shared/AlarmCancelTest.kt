package me.timeto.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.KvDb.Companion.asAlarmSnoozeIntervalId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ringing and snoozing both depend on the interval still qualifying: alarm mode
 * on, timer expired. Anything else must drop the snooze rather than leave it for
 * a later reschedule to revive.
 */
class AlarmCancelTest {

    private suspend fun awaitSubscription() {
        withTimeout(3_000) {
            while (NotificationAlarm.flow.subscriptionCount.value == 0) delay(10)
        }
    }

    private suspend fun CoroutineScope.awaitAlarms(): List<NotificationAlarm> {
        val alarmsDeferred = async { NotificationAlarm.flow.first() }
        awaitSubscription()
        NotificationAlarm.rescheduleAll()
        return withTimeout(3_000) { alarmsDeferred.await() }
    }

    private fun List<NotificationAlarm>.alarmIntervalId(): Int? =
        mapNotNull { (it.type as? NotificationAlarm.Type.Alarm)?.intervalId }.singleOrNull()

    private suspend fun seedSnoozedExpiredInterval() {
        insertActivitySq(id = 1, name = "Work", alarmMode = 1)
        insertIntervalSq(id = 1, time = time() - 700, activityId = 1, note = "deep work #t600")
        KvDb.KEY.ALARM_SNOOZE_UNTIL.upsertInt(time() + 120)
        KvDb.KEY.ALARM_SNOOZE_INTERVAL_ID.upsertInt(1)
        refreshCache()
    }

    private suspend fun snoozeIntervalIdOrNull(): Int? =
        KvDb.KEY.ALARM_SNOOZE_INTERVAL_ID.selectOrNull().asAlarmSnoozeIntervalId()

    private suspend fun disableAlarmMode(activityDb: ActivityDb) {
        activityDb.updateWithValidation(
            name = "Work",
            goalType = null,
            timerType = ActivityDb.TimerType.TimerPicker,
            period = ActivityDb.Period.Weekly(),
            symbol = Symbol.Icon.IconEnum.inbox.toIcon(),
            colorRgba = ColorRgba(1, 2, 3),
            keepScreenOn = false,
            pomodoroTimer = 0,
            timerHints = emptyList(),
            parentActivityDb = null,
            alarmMode = 0,
        )
    }

    @Test
    fun alarmModeOff_clearsSnoozeAndDropsTheAlarm() = runBlocking {
        initTestDb()
        seedSnoozedExpiredInterval()

        // The activity stops qualifying: alarm mode turned off.
        disableAlarmMode(IntervalDb.selectLastOneOrNull()!!.selectActivityDb())

        val alarms = awaitAlarms()

        assertNull(alarms.alarmIntervalId())
        assertNull(snoozeIntervalIdOrNull())
    }

    @Test
    fun newInterval_clearsSnooze() = runBlocking {
        initTestDb()
        seedSnoozedExpiredInterval()

        insertIntervalSq(id = 2, time = time() - 10, activityId = 1, note = "next #t600")

        awaitAlarms()

        assertEquals(2, awaitAlarms().alarmIntervalId())
        assertNull(snoozeIntervalIdOrNull())
    }

    @Test
    fun timerReExtended_clearsSnooze() = runBlocking {
        initTestDb()
        seedSnoozedExpiredInterval()

        IntervalDb.selectLastOneOrNull()!!.updateTimer(timer = 3_600)

        val timerType = IntervalDb.selectLastOneOrNull()!!.buildTimerType() as IntervalDb.TimerType.Timer
        val expected = timerType.finishTime - time()

        val alarms = awaitAlarms()
        val inSeconds = alarms.single { it.type is NotificationAlarm.Type.Alarm }.inSeconds

        // The new finish time wins over the stale snooze deadline.
        assertTrue(
            inSeconds in (expected - 2)..(expected + 1),
            "expected the new finish time $expected, got $inSeconds",
        )
        assertNull(snoozeIntervalIdOrNull())
    }
}
