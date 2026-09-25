package me.timeto.app

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.timeto.shared.NotificationAlarm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ring is a foreground service, so it needs a real device.
 */
@RunWith(AndroidJUnit4::class)
class AlarmRingServiceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val manager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before
    fun setUp() {
        AlarmRingService.stop(context)
        AlarmRingServiceTestSupport.awaitNotRunning()
    }

    @After
    fun tearDown() {
        AlarmRingService.stop(context)
        AlarmRingServiceTestSupport.awaitNotRunning()
        manager.cancel(NotificationAlarm.NOTIFICATION_ID_ALARM)
    }

    @Test
    fun start_ringsAndPostsTheAlarmNotification() {
        AlarmRingService.start(context, intervalId = 7)
        AlarmRingServiceTestSupport.awaitRunning()

        assertTrue(AlarmRingService.isRunning)
        assertEquals(7, AlarmRingService.ringingIntervalId)
        assertNotNull(
            "expected a notification on the alarm id",
            manager.activeNotifications.firstOrNull {
                it.id == NotificationAlarm.NOTIFICATION_ID_ALARM
            },
        )
    }

    @Test
    fun startWhileRinging_isANoOp() {
        AlarmRingService.start(context, intervalId = 7)
        AlarmRingServiceTestSupport.awaitRunning()
        val preparesAfterFirstStart = AlarmRingService.prepareCount

        AlarmRingService.start(context, intervalId = 7)
        AlarmRingServiceTestSupport.awaitSettled()

        assertEquals(preparesAfterFirstStart, AlarmRingService.prepareCount)
        assertEquals(7, AlarmRingService.ringingIntervalId)
    }

    @Test
    fun stop_releasesPlayback() {
        AlarmRingService.start(context, intervalId = 7)
        AlarmRingServiceTestSupport.awaitRunning()

        AlarmRingService.stop(context)
        AlarmRingServiceTestSupport.awaitNotRunning()

        assertFalse(AlarmRingService.isRunning)
        assertEquals(null, AlarmRingService.ringingIntervalId)
    }
}
