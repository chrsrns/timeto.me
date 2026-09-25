package me.timeto.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.timeto.shared.NotificationAlarm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ring must survive a denied full-screen grant and an Alarms-only DND mode,
 * so its shape is load bearing rather than cosmetic.
 */
@RunWith(AndroidJUnit4::class)
class AlarmNotificationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val manager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before
    fun setUp() {
        AlarmRingService.stop(context)
        AlarmRingServiceTestSupport.awaitNotRunning()
        manager.cancel(NotificationAlarm.NOTIFICATION_ID_ALARM)
    }

    @After
    fun tearDown() {
        AlarmRingService.stop(context)
        AlarmRingServiceTestSupport.awaitNotRunning()
        manager.cancel(NotificationAlarm.NOTIFICATION_ID_ALARM)
    }

    private fun ringNotification(): Notification {
        AlarmRingService.start(context, intervalId = 7)
        AlarmRingServiceTestSupport.awaitRunning()
        val posted = manager.activeNotifications.firstOrNull {
            it.id == NotificationAlarm.NOTIFICATION_ID_ALARM
        }
        assertNotNull("the ring must post a notification", posted)
        return posted!!.notification
    }

    @Test
    fun ringChannel_isHighImportance() {
        val channel = NotificationsUtils.channelAlarmRing()

        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        // The channel must not play sound: AlarmRingService owns the audio.
        assertEquals(null, channel.sound)
    }

    @Test
    fun ringNotification_isAlarmCategoryWithASnoozeAction() {
        val notification = ringNotification()

        assertEquals(Notification.CATEGORY_ALARM, notification.category)
        assertTrue(
            "expected a Snooze action",
            notification.actions.any { it.title == "Snooze" },
        )
    }

    @Test
    fun ringNotification_hasNoFullScreenIntentWhenTheGrantIsMissing() {
        if (NotificationsUtils.canUseFullScreenIntent())
            return // Nothing to assert on a device that granted the access.

        // V274/C66: without the special access the ring degrades to a heads-up.
        assertEquals(null, ringNotification().fullScreenIntent)
    }

    @Test
    fun permissions_areDeclared() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val declared = packageInfo.requestedPermissions?.toSet() ?: emptySet()

        listOf(
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "android.permission.WAKE_LOCK",
        ).forEach { permission ->
            assertTrue("missing $permission", declared.contains(permission))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            assertTrue(
                "missing USE_FULL_SCREEN_INTENT",
                declared.contains("android.permission.USE_FULL_SCREEN_INTENT"),
            )
    }
}
