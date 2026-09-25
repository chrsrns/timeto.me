package me.timeto.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.vm.app.AppVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Alarm state changes must reach the producer, otherwise a toggle or an
 * activity edit would not cancel or arm anything until the next interval.
 */
class AppVmAlarmTriggerTest {

    private suspend fun awaitReady(vm: AppVm) {
        withTimeout(10_000) { vm.state.first { it.isAppReady } }
    }

    /**
     * Subscribes for one emission and keeps re-applying the trigger until it
     * lands. `subscriptionCount` cannot be used to gate this: a previous test's
     * subscription may still be counted, so a single trigger can be consumed
     * before this collector is registered. Both triggers are idempotent.
     */
    private suspend fun CoroutineScope.awaitEmissionAfter(
        trigger: suspend () -> Unit,
    ): List<NotificationAlarm> {
        val deferred = async { NotificationAlarm.flow.first() }
        withTimeout(5_000) {
            while (!deferred.isCompleted) {
                trigger()
                delay(50)
            }
        }
        return deferred.await()
    }

    private fun List<NotificationAlarm>.alarmIntervalId(): Int? =
        mapNotNull { (it.type as? NotificationAlarm.Type.Alarm)?.intervalId }.singleOrNull()

    @Test
    fun alarmModeDefaultChange_andActivityWrite_reschedule(): Unit = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work", alarmMode = null)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1, note = "deep work #t600")
        refreshCache()

        val vm = AppVm()
        try {
            awaitReady(vm)

            // The activity inherits a disabled default, so no alarm is scheduled.
            val initial = awaitEmissionAfter { NotificationAlarm.rescheduleAll() }
            assertNull(initial.alarmIntervalId())

            // Flipping the global default must reach the producer on its own.
            val afterToggle = awaitEmissionAfter {
                KvDb.KEY.ALARM_MODE_DEFAULT.upsertBoolean(true)
            }
            assertEquals(1, afterToggle.alarmIntervalId())

            // So must an activity write, which is table-level and fires for
            // unrelated edits too.
            awaitEmissionAfter {
                ActivityDb.selectAll().first().updateChecklistHint(1)
            }
        } finally {
            vm.onDestroy()
        }
    }
}
