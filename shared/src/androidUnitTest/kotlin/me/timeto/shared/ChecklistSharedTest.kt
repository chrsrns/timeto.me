package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChecklistSharedTest {

    @Test
    fun twoActivities_shareSameChecklistState() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "A #c1")
        insertActivitySq(id = 2, name = "B #c1")
        val list = ChecklistDb.insertWithValidation(name = "list", isResetOnDayStarts = false)
        ChecklistItemDb.insertWithValidation(text = "i1", checklist = list, isChecked = false)
        ChecklistItemDb.insertWithValidation(text = "i2", checklist = list, isChecked = false)
        refreshCache()

        fun checklistsFor(activityId: Int) =
            Cache.activitiesDb.first { it.id == activityId }.name.textFeatures().checklistsDb

        // Both activities resolve the same checklist with the same items.
        assertEquals(listOf(1), checklistsFor(1).map { it.id })
        assertEquals(listOf(1), checklistsFor(2).map { it.id })
        val itemsA = checklistsFor(1).first().getItemsCached()
        val itemsB = checklistsFor(2).first().getItemsCached()
        assertEquals(itemsA.map { it.id }, itemsB.map { it.id })
        assertFalse(itemsA.all { it.isChecked })

        // Toggling via one list is visible from both activity views.
        ChecklistItemDb.selectSorted().first().toggle()
        refreshCache()
        val itemsAAfter = checklistsFor(1).first().getItemsCached()
        val itemsBAfter = checklistsFor(2).first().getItemsCached()
        assertEquals(itemsAAfter.map { it.isChecked }, itemsBAfter.map { it.isChecked })
        assertTrue(itemsAAfter.count { it.isChecked } == 1)
    }
}
