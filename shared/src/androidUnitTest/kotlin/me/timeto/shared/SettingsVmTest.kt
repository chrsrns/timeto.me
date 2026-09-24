package me.timeto.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import kotlin.test.assertTrue

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
            awaitKv(key = KvDb.KEY.ALARM_MODE_DEFAULT, expected = "1")

            assertTrue(vm.state.value.isAlarmModeDefaultEnabled)
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
            awaitKv(key = KvDb.KEY.ALARM_SNOOZE_SECONDS, expected = "600")

            assertEquals(600, vm.state.value.alarmSnoozeSeconds)
            assertEquals("10 min", vm.state.value.alarmSnoozeNote)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun awaitKv(key: KvDb.KEY, expected: String) {
        withTimeout(5_000) {
            while (key.selectStringOrNull() != expected)
                delay(50)
        }
    }
}
