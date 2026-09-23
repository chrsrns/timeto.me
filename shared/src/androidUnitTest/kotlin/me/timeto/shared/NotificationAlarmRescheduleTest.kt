package me.timeto.shared

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.IntervalDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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
}
