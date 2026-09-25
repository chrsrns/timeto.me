package me.timeto.shared

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test

/**
 * The producer reschedules on every activity write, so a consumer that takes a
 * value and then stops collecting must not be able to suspend it.
 */
class NotificationAlarmFlowTest {

    @Test
    fun wedgedConsumer_doesNotSuspendTheProducer() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work")
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1, note = "deep work #t600")
        refreshCache()

        // Takes the first value, then never collects again while staying subscribed.
        val wedged = launch {
            NotificationAlarm.flow.collect { awaitCancellation() }
        }
        withTimeout(3_000) {
            while (NotificationAlarm.flow.subscriptionCount.value == 0) delay(10)
        }

        // Unbuffered, every emit after the first would suspend here forever.
        withTimeout(5_000) {
            repeat(5) { NotificationAlarm.rescheduleAll() }
        }

        wedged.cancel()
    }
}
