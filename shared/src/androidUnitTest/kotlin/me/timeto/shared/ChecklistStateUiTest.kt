package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.vm.checklists.ChecklistStateUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChecklistStateUiTest {

    private var seedCounter = 0

    private suspend fun seedChecklistWithItems(vararg checked: Boolean): ChecklistDb {
        val list = ChecklistDb.insertWithValidation(name = "list${seedCounter++}", isResetOnDayStarts = false)
        checked.forEachIndexed { i, isChecked ->
            ChecklistItemDb.insertWithValidation(text = "i$i", checklist = list, isChecked = isChecked)
        }
        return list
    }

    private suspend fun itemsOf(list: ChecklistDb) =
        ChecklistItemDb.selectSorted().filter { it.list_id == list.id }

    @Test
    fun build_allNoneMixedEmpty_states() = runBlocking {
        initTestDb()

        // Empty list -> Completed (vacuous `all {}` wins over `none {}`)
        val emptyList = seedChecklistWithItems()
        val emptyState = ChecklistStateUi.build(emptyList, itemsOf(emptyList))
        assertIs<ChecklistStateUi.Completed>(emptyState)
        assertEquals("Uncheck All", emptyState.actionDesc)

        val allChecked = seedChecklistWithItems(true, true)
        val allState = ChecklistStateUi.build(allChecked, itemsOf(allChecked))
        assertIs<ChecklistStateUi.Completed>(allState)
        assertEquals("Uncheck All", allState.actionDesc)

        val noneChecked = seedChecklistWithItems(false, false)
        val noneState = ChecklistStateUi.build(noneChecked, itemsOf(noneChecked))
        assertIs<ChecklistStateUi.Empty>(noneState)
        assertEquals("Check All", noneState.actionDesc)

        val mixed = seedChecklistWithItems(true, false)
        val mixedState = ChecklistStateUi.build(mixed, itemsOf(mixed))
        assertIs<ChecklistStateUi.Partial>(mixedState)
        assertEquals("Uncheck All", mixedState.actionDesc)
    }

    @Test
    fun onClick_empty_checksAll() = runBlocking {
        initTestDb()
        val list = seedChecklistWithItems(false, false)
        ChecklistStateUi.build(list, itemsOf(list)).onClick()
        withTimeout(3_000) {
            while (itemsOf(list).any { !it.isChecked }) delay(20)
        }
        assertTrue(itemsOf(list).all { it.isChecked })
    }

    @Test
    fun onClick_completedAndPartial_uncheckAll() = runBlocking {
        initTestDb()

        val completedList = seedChecklistWithItems(true, true)
        ChecklistStateUi.build(completedList, itemsOf(completedList)).onClick()
        withTimeout(3_000) {
            while (itemsOf(completedList).any { it.isChecked }) delay(20)
        }
        assertTrue(itemsOf(completedList).all { !it.isChecked })

        val partialList = seedChecklistWithItems(true, false)
        ChecklistStateUi.build(partialList, itemsOf(partialList)).onClick()
        withTimeout(3_000) {
            while (itemsOf(partialList).any { it.isChecked }) delay(20)
        }
        assertTrue(itemsOf(partialList).all { !it.isChecked })
    }
}
