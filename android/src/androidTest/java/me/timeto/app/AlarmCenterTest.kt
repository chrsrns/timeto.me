package me.timeto.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.timeto.shared.LiveActivity
import me.timeto.shared.NotificationAlarm
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.KvDb.Companion.asAlarmSnoozeIntervalId
import me.timeto.shared.db.KvDb.Companion.asAlarmSnoozeUntil
import me.timeto.shared.time
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmCenterTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        // A still-armed ring from an earlier test can fire mid-test and stop the
        // service between its start and its startForeground call, which the
        // system then records as a violation.
        AlarmCenter.cancelAlarmRing()
        AlarmRingService.stop(context)
        AlarmRingServiceTestSupport.awaitNotRunning()
    }

    @After
    fun tearDown() {
        AlarmCenter.cancelAlarmRing()
        AlarmRingService.stop(context)
        AlarmRingServiceTestSupport.awaitNotRunning()
        AlarmRingServiceTestSupport.clearSnoozeKeys()
    }

    private fun alarmNotification(
        intervalId: Int,
        inSeconds: Int,
    ): NotificationAlarm = NotificationAlarm(
        title = "Time Is Over",
        text = "text",
        inSeconds = inSeconds,
        type = NotificationAlarm.Type.Alarm(intervalId = intervalId),
        // A null note makes LiveActivity resolve the activity name through the
        // cache, which throws when the test database has no such activity.
        liveActivity = LiveActivity(
            IntervalDb(id = 1, time = 0, activityId = 1, note = "deep work"),
        ),
    )

    @Test
    fun armedAlarm_startsTheServiceForTheArmedInterval() {
        AlarmCenter.scheduleAlarmRing(intervalId = 7, inSeconds = 0)
        AlarmRingServiceTestSupport.awaitRunning()

        // The service has to learn the interval from the armed intent, otherwise
        // the cancel predicate can never match it.
        assertEquals(7, AlarmRingService.ringingIntervalId)
    }

    @Test
    fun armedRing_survivesARescheduleWithTheSameExpiredAlarm() {
        AlarmCenter.scheduleAlarmRing(intervalId = 7, inSeconds = 0)
        AlarmRingServiceTestSupport.awaitRunning()

        AlarmCenter.cancelAllAlarms(
            stopRingService = true,
            notifications = listOf(alarmNotification(intervalId = 7, inSeconds = 0)),
        )
        AlarmRingServiceTestSupport.awaitSettled()

        assertTrue(AlarmRingService.isRunning)
    }

    @Test
    fun cancelAllAlarms_keepsRingingForTheSameExpiredInterval() {
        AlarmRingService.start(context, intervalId = 7)
        AlarmRingServiceTestSupport.awaitRunning()

        AlarmCenter.cancelAllAlarms(
            stopRingService = true,
            notifications = listOf(alarmNotification(intervalId = 7, inSeconds = 0)),
        )
        AlarmRingServiceTestSupport.awaitSettled()

        assertTrue(AlarmRingService.isRunning)
    }

    @Test
    fun cancelAllAlarms_stopsRingingForASupersededInterval() {
        AlarmRingService.start(context, intervalId = 7)
        AlarmRingServiceTestSupport.awaitRunning()

        AlarmCenter.cancelAllAlarms(
            stopRingService = true,
            notifications = listOf(alarmNotification(intervalId = 8, inSeconds = 0)),
        )
        AlarmRingServiceTestSupport.awaitNotRunning()

        assertFalse(AlarmRingService.isRunning)
    }

    @Test
    fun cancelAllAlarms_stopsRingingWhenTheAlarmIsNoLongerExpired() {
        AlarmRingService.start(context, intervalId = 7)
        AlarmRingServiceTestSupport.awaitRunning()

        // Same interval, but the timer was re-extended, so nothing is due now.
        AlarmCenter.cancelAllAlarms(
            stopRingService = true,
            notifications = listOf(alarmNotification(intervalId = 7, inSeconds = 300)),
        )
        AlarmRingServiceTestSupport.awaitNotRunning()

        assertFalse(AlarmRingService.isRunning)
    }

    @Test
    fun cancelAllAlarms_stopsRingingWhenTheListIsEmpty() {
        AlarmRingService.start(context, intervalId = 7)
        AlarmRingServiceTestSupport.awaitRunning()

        AlarmCenter.cancelAllAlarms(stopRingService = true, notifications = emptyList())
        AlarmRingServiceTestSupport.awaitNotRunning()

        assertFalse(AlarmRingService.isRunning)
    }

    @Test
    fun snooze_writesTheDeadlineForTheRingingInterval() = runBlocking {
        AlarmRingService.start(context, intervalId = 7)
        AlarmRingServiceTestSupport.awaitRunning()

        AlarmRingService.snooze(context, intervalId = 7)
        AlarmRingServiceTestSupport.awaitNotRunning()

        assertEquals(
            7,
            KvDb.KEY.ALARM_SNOOZE_INTERVAL_ID.selectOrNull().asAlarmSnoozeIntervalId(),
        )
        assertTrue(
            KvDb.KEY.ALARM_SNOOZE_UNTIL.selectOrNull().asAlarmSnoozeUntil() > time(),
        )
    }
}
