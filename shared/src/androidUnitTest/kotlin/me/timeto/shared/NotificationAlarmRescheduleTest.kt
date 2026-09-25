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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationAlarmRescheduleTest {

    private suspend fun awaitSubscription() {
        withTimeout(3_000) {
            while (NotificationAlarm.flow.subscriptionCount.value == 0) delay(10)
        }
    }

    @Test
    fun reschedule_timerEmitsBreakAndNoActivity() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work")
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1, note = "deep work #t600")
        refreshCache()

        val alarmsDeferred = async { NotificationAlarm.flow.first() }
        val liveDeferred = async { LiveActivity.flow.first() }
        awaitSubscription()
        NotificationAlarm.rescheduleAll()

        val alarms = withTimeout(3_000) { alarmsDeferred.await() }
        assertIs<LiveActivity>(withTimeout(3_000) { liveDeferred.await() })

        val breakAlarms = alarms.filter { it.type is NotificationAlarm.Type.TimeToBreak }
        assertEquals(1, breakAlarms.size)
        assertTrue(breakAlarms.single().inSeconds > 0)

        val noActivityDays = alarms
            .mapNotNull { (it.type as? NotificationAlarm.Type.NoActivity)?.day }
            .sorted()
        assertEquals((1..7).toList(), noActivityDays)
        assertTrue(alarms.filterIsInstance<NotificationAlarm>().all { it.inSeconds > 0 })
    }

    @Test
    fun reschedule_stopwatchInterval_noBreakAlarm() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work")
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1, note = null)
        refreshCache()

        val alarmsDeferred = async { NotificationAlarm.flow.first() }
        awaitSubscription()
        NotificationAlarm.rescheduleAll()

        val alarms = withTimeout(3_000) { alarmsDeferred.await() }
        assertTrue(alarms.none { it.type is NotificationAlarm.Type.TimeToBreak })
        assertEquals(
            (1..7).toList(),
            alarms.mapNotNull { (it.type as? NotificationAlarm.Type.NoActivity)?.day }.sorted(),
        )
    }

    @Test
    fun reschedule_noInterval_clearsAlarms() = runBlocking {
        initTestDb()

        val alarmsDeferred = async { NotificationAlarm.flow.first() }
        awaitSubscription()
        NotificationAlarm.rescheduleAll()

        // V212: without intervals the alarm list must be cleared, not crash.
        assertEquals(emptyList(), withTimeout(3_000) { alarmsDeferred.await() })
    }

    ///

    private suspend fun CoroutineScope.awaitAlarms(): List<NotificationAlarm> {
        val alarmsDeferred = async { NotificationAlarm.flow.first() }
        awaitSubscription()
        NotificationAlarm.rescheduleAll()
        return withTimeout(3_000) { alarmsDeferred.await() }
    }

    private fun List<NotificationAlarm>.alarm(): NotificationAlarm.Type.Alarm? =
        mapNotNull { it.type as? NotificationAlarm.Type.Alarm }.singleOrNull()

    @Test
    fun alarmMode_armedTimer_emitsSingleAlarmAndSuppressesNag() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work", alarmMode = 1)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1, note = "deep work #t600")
        KvDb.KEY.TIMER_EXPIRED_REPEAT_SECONDS.upsertInt(300)
        refreshCache()

        val alarms = awaitAlarms()

        assertEquals(1, alarms.alarm()?.intervalId)
        assertTrue(alarms.none { it.type is NotificationAlarm.Type.TimeToBreak })
        assertTrue(alarms.none { it.type is NotificationAlarm.Type.ExpiredRepeat })
        assertTrue(alarms.single { it.type is NotificationAlarm.Type.Alarm }.inSeconds > 0)
        // No-activity reminders are unaffected by alarm mode.
        assertEquals(
            (1..7).toList(),
            alarms.mapNotNull { (it.type as? NotificationAlarm.Type.NoActivity)?.day }.sorted(),
        )
    }

    @Test
    fun alarmMode_expiredTimer_emitsImmediateAlarm() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work", alarmMode = 1)
        insertIntervalSq(id = 1, time = time() - 700, activityId = 1, note = "deep work #t600")
        refreshCache()

        val alarm = awaitAlarms().single { it.type is NotificationAlarm.Type.Alarm }

        // finishTime is 100s in the past -> ring now, not in the future.
        assertEquals(0, alarm.inSeconds)
    }

    @Test
    fun alarmModeFromGlobalDefault_appliesToInheritingActivity() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work", alarmMode = null)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1, note = "deep work #t600")
        KvDb.KEY.ALARM_MODE_DEFAULT.upsertBoolean(true)
        refreshCache()

        assertEquals(1, awaitAlarms().alarm()?.intervalId)
    }

    @Test
    fun alarmModeDisabled_stillEmitsBreakAndRepeat() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work", alarmMode = 0)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1, note = "deep work #t600")
        KvDb.KEY.TIMER_EXPIRED_REPEAT_SECONDS.upsertInt(300)
        refreshCache()

        val alarms = awaitAlarms()

        assertNull(alarms.alarm())
        assertEquals(1, alarms.count { it.type is NotificationAlarm.Type.TimeToBreak })
        assertTrue(alarms.any { it.type is NotificationAlarm.Type.ExpiredRepeat })
    }

    @Test
    fun alarmMode_stopwatchInterval_noAlarm() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work", alarmMode = 1)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1, note = null)
        refreshCache()

        assertNull(awaitAlarms().alarm())
    }

    @Test
    fun alarmMode_overdueTimer_noAlarm() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work", alarmMode = 1)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1, note = "break #t100000300")
        KvDb.KEY.TIMER_EXPIRED_REPEAT_SECONDS.upsertInt(300)
        refreshCache()

        val alarms = awaitAlarms()

        // Break state is not a new expiry: the repeat nag still applies.
        assertNull(alarms.alarm())
        assertTrue(alarms.any { it.type is NotificationAlarm.Type.ExpiredRepeat })
    }

    @Test
    fun alarmMode_nonAndroid_keepsBreakAndRepeat() = runBlocking {
        initTestDb(os = SystemInfo.Os.Ios("test"))
        insertActivitySq(id = 1, name = "Work", alarmMode = 1)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1, note = "deep work #t600")
        KvDb.KEY.TIMER_EXPIRED_REPEAT_SECONDS.upsertInt(300)
        refreshCache()

        val alarms = awaitAlarms()

        assertNull(alarms.alarm())
        assertEquals(1, alarms.count { it.type is NotificationAlarm.Type.TimeToBreak })
        assertTrue(alarms.any { it.type is NotificationAlarm.Type.ExpiredRepeat })
    }
}
