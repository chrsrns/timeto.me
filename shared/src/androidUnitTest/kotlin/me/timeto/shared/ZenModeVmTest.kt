package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.vm.zen_mode.ZenModeVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ZenModeVmTest {

    private suspend fun seedRunningInterval(activityId: Int = 7) {
        insertActivitySq(id = activityId)
        insertIntervalSq(id = 1, time = time() - 30, activityId = activityId, note = "#t600")
        seedTaskFolders()
        refreshCache()
    }

    @Test
    fun checklistVisibility_persistAndParse() = runBlocking {
        initTestDb()
        seedRunningInterval()

        val vm = ZenModeVm()
        try {
            assertTrue(vm.state.value.initShowChecklist) // 7 not hidden
        } finally {
            vm.onDestroy()
        }

        vm.hideChecklist()
        withTimeout(5_000) {
            while (KvDb.KEY.ZEN_MODE_CHECKLISTS_VISIBILITY.selectStringOrNull() != "7")
                delay(50)
        }

        // A fresh VM reads the persisted hidden set.
        val vm2 = ZenModeVm()
        try {
            assertFalse(vm2.state.value.initShowChecklist)
        } finally {
            vm2.onDestroy()
        }

        // Garbage segments are dropped by the comma-join parse.
        KvDb.KEY.ZEN_MODE_CHECKLISTS_VISIBILITY.upsertString("7,abc,9")
        val vm3 = ZenModeVm()
        try {
            assertFalse(vm3.state.value.initShowChecklist) // 7 hidden
        } finally {
            vm3.onDestroy()
        }

        // showChecklist removes the id.
        KvDb.KEY.ZEN_MODE_CHECKLISTS_VISIBILITY.upsertString("7")
        val vm4 = ZenModeVm()
        try {
            vm4.showChecklist()
            withTimeout(5_000) {
                while (KvDb.KEY.ZEN_MODE_CHECKLISTS_VISIBILITY.selectStringOrNull() != "")
                    delay(50)
            }
        } finally {
            vm4.onDestroy()
        }
        val vm5 = ZenModeVm()
        try {
            assertTrue(vm5.state.value.initShowChecklist)
        } finally {
            vm5.onDestroy()
        }
    }

    @Test
    fun zenMode_doesNotBlockTimer_onlyTicks() = runBlocking {
        initTestDb()
        seedRunningInterval()

        val vm = ZenModeVm()
        try {
            val timerStateUi = vm.state.value.timerStateUi
            assertTrue(timerStateUi.timerType is IntervalDb.TimerType.Timer)
            assertTrue(timerStateUi.timerText.isNotBlank())

            // The 1s loop only bumps idToUpdate for redraws.
            val tickBefore = vm.state.value.idToUpdate
            delay(1_300)
            assertTrue(vm.state.value.idToUpdate > tickBefore)
        } finally {
            vm.onDestroy()
        }
    }
}
