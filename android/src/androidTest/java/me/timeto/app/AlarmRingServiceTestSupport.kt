package me.timeto.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail

/**
 * Waiting helpers for service state. Instrumented tests must not assume the
 * service reaches its target state on the same frame as the start call.
 */
object AlarmRingServiceTestSupport {

    private const val TIMEOUT_MILLIS = 5_000L

    /**
     * A foreground service notification is suppressed while POST_NOTIFICATIONS is
     * denied, so the ring would run with no notification to assert on.
     */
    fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    fun awaitRunning() =
        await("service never started ringing") { AlarmRingService.isRunning }

    fun awaitNotRunning() =
        await("service never stopped ringing") { !AlarmRingService.isRunning }

    /**
     * `startForeground` hands the notification to the system asynchronously, so
     * the active list can lag the service being up by a moment.
     */
    fun awaitNotification(id: Int): Notification {
        var found: Notification? = null
        val manager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        await("notification $id was never posted") {
            found = manager.activeNotifications.firstOrNull { it.id == id }?.notification
            found != null
        }
        return found!!
    }

    /** Preparation is asynchronous, so success has to be awaited, not assumed. */
    fun awaitPreparedAtLeast(count: Int) =
        await("playback never reached the prepared state") { AlarmRingService.preparedCount >= count }

    fun awaitSettled() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition())
                return
            Thread.sleep(25)
        }
        fail(message)
    }
}
