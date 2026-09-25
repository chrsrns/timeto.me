package me.timeto.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.KvDb
import me.timeto.shared.vm.settings.SettingsVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsVmTest {

    @Test
    fun alarmModeDefault_defaultsOff_roundTripsKv() = runBlocking {
        initTestDb()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            refreshCache()
            val vm = SettingsVm()
            assertFalse(vm.state.value.isAlarmModeDefaultEnabled)

            vm.setAlarmModeDefaultEnabled(true)

            // The KV flow can deliver the initial null after the optimistic update,
            // so wait for the state the UI actually renders.
            withTimeout(5_000) { vm.state.first { it.isAlarmModeDefaultEnabled } }
            assertEquals("1", KvDb.KEY.ALARM_MODE_DEFAULT.selectStringOrNull())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun alarmSnoozeSeconds_defaults300_roundTripsKv() = runBlocking {
        initTestDb()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            refreshCache()
            val vm = SettingsVm()
            assertEquals(300, vm.state.value.alarmSnoozeSeconds)
            assertEquals("5 min", vm.state.value.alarmSnoozeNote)

            vm.setAlarmSnoozeSeconds(600)

            withTimeout(5_000) { vm.state.first { it.alarmSnoozeSeconds == 600 } }
            assertEquals("600", KvDb.KEY.ALARM_SNOOZE_SECONDS.selectStringOrNull())
            assertEquals("10 min", vm.state.value.alarmSnoozeNote)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
