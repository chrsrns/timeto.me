package me.timeto.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.KvDb
import me.timeto.shared.vm.main.MainTabsVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The android side turns a lastIntervalId change into tab switch + haptic
// via LaunchedEffect; what is executable on the JVM is the state update.
class MainTabsVmTest {

    @Test
    fun lastIntervalId_tracksNewestInterval(): Unit = runBlocking {
        initTestDb()
        seedTaskFolders()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = time() - 60, activityId = 1)
        refreshCache()

        val vm = MainTabsVm()
        try {
            assertEquals(1, vm.state.value.lastIntervalId)
            assertNull(vm.state.value.batteryLevel) // no platform emitter in tests

            insertIntervalSq(id = 2, time = time(), activityId = 1)
            withTimeout(5_000) { vm.state.first { it.lastIntervalId == 2 } }
        } finally {
            vm.onDestroy()
        }
    }
}
