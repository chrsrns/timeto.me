package me.timeto.shared

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    private fun List<List<NotificationAlarm>>.latestAlarm(): NotificationAlarm.Type.Alarm? =
        lastOrNull()?.mapNotNull { it.type as? NotificationAlarm.Type.Alarm }?.singleOrNull()

    @Test
    fun alarmModeDefaultChange_andActivityWrite_reschedule() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work", alarmMode = null)
        insertIntervalSq(id = 1, time = time() - 10, activityId = 1, note = "deep work #t600")
        refreshCache()

        val vm = AppVm()
        val emissions = mutableListOf<List<NotificationAlarm>>()
        var job: Job? = null
        try {
            awaitReady(vm)

            job = launch { NotificationAlarm.flow.collect { emissions.add(it) } }
            withTimeout(3_000) {
                while (NotificationAlarm.flow.subscriptionCount.value == 0) delay(10)
            }

            // The activity inherits a disabled default, so no alarm is scheduled yet.
            assertNull(emissions.latestAlarm())

            KvDb.KEY.ALARM_MODE_DEFAULT.upsertBoolean(true)
            withTimeout(5_000) { while (emissions.latestAlarm() == null) delay(10) }
            assertEquals(1, emissions.latestAlarm()?.intervalId)

            val before = emissions.size
            ActivityDb.selectAll().first().updateChecklistHint(1)
            withTimeout(5_000) { while (emissions.size <= before) delay(10) }
        } finally {
            job?.cancel()
            vm.onDestroy()
        }
    }
}
