package me.timeto.shared

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.vm.checklists.form.ChecklistFormItemsVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChecklistFormItemsVmTest {

    @Test
    fun isDoneAllowed_empty_alerts() = runBlocking {
        initTestDb()
        val list = ChecklistDb.insertWithValidation(name = "list", isResetOnDayStarts = false)
        refreshCache()

        val vm = ChecklistFormItemsVm(list)
        val dialogs = TestDialogsManager()

        assertFalse(vm.isDoneAllowed(dialogs))
        val alert = withTimeout(3_000) { dialogs.alerts.receive() }
        assertEquals("Please add at least one item", alert)
        vm.onDestroy()
    }

    @Test
    fun isDoneAllowed_withItem_true() = runBlocking {
        initTestDb()
        val list = ChecklistDb.insertWithValidation(name = "list", isResetOnDayStarts = false)
        ChecklistItemDb.insertWithValidation(text = "i", checklist = list, isChecked = false)
        refreshCache()

        val vm = ChecklistFormItemsVm(list)
        val dialogs = TestDialogsManager()

        assertTrue(vm.isDoneAllowed(dialogs))
        assertTrue(dialogs.alerts.isEmpty)
        vm.onDestroy()
    }
}
