package me.timeto.shared

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChecklistItemDbTest {

    private suspend fun seedChecklist(name: String = "list"): ChecklistDb =
        ChecklistDb.insertWithValidation(name = name, isResetOnDayStarts = false)

    @Test
    fun insert_blankText_throws() = runBlocking {
        initTestDb()
        val list = seedChecklist()
        val ex = assertFailsWith<UiException> {
            ChecklistItemDb.insertWithValidation(text = "  ", checklist = list, isChecked = false)
        }
        assertEquals("Empty text", ex.message)
        assertTrue(ChecklistItemDb.selectSorted().isEmpty())
    }

    @Test
    fun update_blankText_throws() = runBlocking {
        initTestDb()
        val list = seedChecklist()
        ChecklistItemDb.insertWithValidation(text = "i", checklist = list, isChecked = false)
        val itemDb = ChecklistItemDb.selectSorted().first()
        val ex = assertFailsWith<UiException> {
            itemDb.updateTextWithValidation("   ")
        }
        assertEquals("Empty text", ex.message)
        assertEquals("i", ChecklistItemDb.selectSorted().first().text)
    }

    @Test
    fun insert_idAndSortGlobalMaxPlusOne() = runBlocking {
        initTestDb()
        val listA = seedChecklist("a")
        val listB = seedChecklist("b")
        ChecklistItemDb.insertWithValidation(text = "a1", checklist = listA, isChecked = false)
        ChecklistItemDb.insertWithValidation(text = "b1", checklist = listB, isChecked = false)
        ChecklistItemDb.insertWithValidation(text = "a2", checklist = listA, isChecked = false)
        val itemsDb = ChecklistItemDb.selectSorted()
        // ids and sorts are global across lists, not per-list
        assertEquals(listOf(1, 2, 3), itemsDb.map { it.id })
        assertEquals(listOf(0, 1, 2), itemsDb.map { it.sort })
    }

    @Test
    fun toggle_checkTimeSemantics() = runBlocking {
        initTestDb()
        val list = seedChecklist()
        ChecklistItemDb.insertWithValidation(text = "i1", checklist = list, isChecked = false)
        ChecklistItemDb.insertWithValidation(text = "i2", checklist = list, isChecked = false)

        val itemDb = ChecklistItemDb.selectSorted().first()
        assertEquals(0, itemDb.check_time)

        itemDb.toggle()
        val checked = ChecklistItemDb.selectSorted().first { it.id == itemDb.id }
        assertTrue(checked.check_time > 0)
        assertTrue(checked.isChecked)

        checked.toggle()
        assertEquals(0, ChecklistItemDb.selectSorted().first { it.id == itemDb.id }.check_time)

        // toggleByList affects every item of the list
        ChecklistItemDb.toggleByList(list, checkOrUncheck = true)
        assertTrue(ChecklistItemDb.selectSorted().all { it.check_time > 0 })
        ChecklistItemDb.toggleByList(list, checkOrUncheck = false)
        assertTrue(ChecklistItemDb.selectSorted().all { it.check_time == 0 })
    }

    @Test
    fun deleteWithDependencies_itemsGone_danglingToken() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "A #c1")
        val list = seedChecklist() // id = 1
        ChecklistItemDb.insertWithValidation(text = "i", checklist = list, isChecked = true)
        refreshCache()

        val activityDb = Cache.activitiesDb.first()
        assertEquals(listOf(1), activityDb.name.textFeatures().checklistsDb.map { it.id })

        list.deleteWithDependencies()

        assertTrue(ChecklistItemDb.selectSorted().isEmpty())
        assertTrue(ChecklistDb.selectAsc().isEmpty())
        // The #c1 token stays in text but resolves to nothing (dangling).
        refreshCache()
        val activityDbAfter = Cache.activitiesDb.first()
        assertTrue("#c1" in activityDbAfter.name)
        assertTrue(activityDbAfter.name.textFeatures().checklistsDb.isEmpty())
    }

    @Test
    fun backupRestore_tupleRoundtrip() = runBlocking {
        initTestDb()
        val list = seedChecklist()

        // Checklist tuple: [id, name, reset_day]
        val listJson = list.backupable__backup().jsonArray
        assertEquals(list.id, listJson.getInt(0))
        assertEquals("list", listJson.getString(1))
        assertEquals(0, listJson.getInt(2))

        ChecklistItemDb.insertWithValidation(text = "i", checklist = list, isChecked = true)
        val itemDb = ChecklistItemDb.selectSorted().first()

        // Item tuple: [id, text, list_id, check_time, sort]
        val itemJson = itemDb.backupable__backup().jsonArray
        assertEquals(itemDb.id, itemJson.getInt(0))
        assertEquals("i", itemJson.getString(1))
        assertEquals(list.id, itemJson.getInt(2))
        assertTrue(itemJson.getInt(3) > 0)
        assertEquals(0, itemJson.getInt(4))

        // Roundtrip: delete originals (restore re-inserts original ids), restore, compare.
        itemDb.delete()
        list.backupable__delete()
        ChecklistDb.backupable__restore(list.backupable__backup())
        ChecklistItemDb.backupable__restore(itemDb.backupable__backup())

        val restoredList = ChecklistDb.selectAsc().first()
        assertEquals(list.id, restoredList.id)
        assertEquals(list.name, restoredList.name)
        assertEquals(list.reset_day, restoredList.reset_day)

        val restoredItem = ChecklistItemDb.selectSorted().first()
        assertEquals(itemDb.id, restoredItem.id)
        assertEquals(itemDb.text, restoredItem.text)
        assertEquals(itemDb.list_id, restoredItem.list_id)
        assertEquals(itemDb.check_time, restoredItem.check_time)
        assertEquals(itemDb.sort, restoredItem.sort)
    }
}
