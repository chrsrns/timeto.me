package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChecklistDbTest {

    @Test
    fun insert_blankName_throws() = runBlocking {
        initTestDb()
        val ex = assertFailsWith<UiException> {
            ChecklistDb.insertWithValidation(name = "   ", isResetOnDayStarts = false)
        }
        assertEquals("Empty name", ex.message)
        assertTrue(ChecklistDb.selectAsc().isEmpty())
    }

    @Test
    fun insert_duplicateName_caseInsensitive_throws(): Unit = runBlocking {
        initTestDb()
        ChecklistDb.insertWithValidation(name = "Work", isResetOnDayStarts = false)
        val ex = assertFailsWith<UiException> {
            ChecklistDb.insertWithValidation(name = "work", isResetOnDayStarts = false)
        }
        assertEquals("work already exists", ex.message)
        assertFailsWith<UiException> {
            ChecklistDb.insertWithValidation(name = "WORK", isResetOnDayStarts = false)
        }
    }

    @Test
    fun update_duplicateName_throws_selfExcluded() = runBlocking {
        initTestDb()
        ChecklistDb.insertWithValidation(name = "Work", isResetOnDayStarts = false)
        ChecklistDb.insertWithValidation(name = "Inbox", isResetOnDayStarts = false)
        val inboxDb = ChecklistDb.selectAsc().first { it.name == "Inbox" }

        val ex = assertFailsWith<UiException> {
            inboxDb.updateWithValidation(name = "work", isResetOnDayStarts = false)
        }
        assertEquals("work already exists", ex.message)

        // Self-excluded: renaming "Work" to "WORK" (same name, diff case) is fine.
        val workDb = ChecklistDb.selectAsc().first { it.name == "Work" }
        workDb.updateWithValidation(name = "WORK", isResetOnDayStarts = false)
        assertEquals("WORK", ChecklistDb.selectAsc().first { it.id == workDb.id }.name)
    }

    @Test
    fun insert_idIsMaxPlusOne() = runBlocking {
        initTestDb()
        val first = ChecklistDb.insertWithValidation(name = "a", isResetOnDayStarts = false)
        assertEquals(1, first.id)
        val second = ChecklistDb.insertWithValidation(name = "b", isResetOnDayStarts = false)
        assertEquals(2, second.id)
    }

    @Test
    fun resetIfNeeded_boundaries() = runBlocking {
        initTestDb()
        val today = UnixTime().localDay

        // No reset flag -> reset_day = 0 -> no-op
        val noReset = ChecklistDb.insertWithValidation(name = "a", isResetOnDayStarts = false)
        ChecklistItemDb.insertWithValidation(text = "i1", checklist = noReset, isChecked = true)
        assertEquals(0, noReset.reset_day)
        noReset.resetIfNeeded(today)
        assertTrue(ChecklistItemDb.selectSorted().first().isChecked)

        // reset_day == today -> boundary, no-op
        val resetting = ChecklistDb.insertWithValidation(name = "b", isResetOnDayStarts = true)
        ChecklistItemDb.insertWithValidation(text = "i2", checklist = resetting, isChecked = true)
        assertEquals(today, resetting.reset_day)
        resetting.resetIfNeeded(today)
        assertTrue(
            ChecklistItemDb.selectSorted().first { it.list_id == resetting.id }.isChecked
        )
        assertEquals(today, ChecklistDb.selectAsc().first { it.id == resetting.id }.reset_day)

        // reset_day < today -> uncheck all + bump reset_day
        resetting.resetIfNeeded(today + 1)
        val item = ChecklistItemDb.selectSorted().first { it.list_id == resetting.id }
        assertEquals(0, item.check_time)
        assertEquals(today + 1, ChecklistDb.selectAsc().first { it.id == resetting.id }.reset_day)
    }
}
