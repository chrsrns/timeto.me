package me.timeto.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail

/**
 * Waiting helpers for service state. Instrumented tests must not assume the
 * service reaches its target state on the same frame as the start call.
 */
object AlarmRingServiceTestSupport {

    private const val TIMEOUT_MILLIS = 5_000L

    fun awaitRunning() =
        await("service never started ringing") { AlarmRingService.isRunning }

    fun awaitNotRunning() =
        await("service never stopped ringing") { !AlarmRingService.isRunning }

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
