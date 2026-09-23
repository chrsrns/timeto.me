package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.TaskFolderDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.math.absoluteValue

class TaskFolderDbTest {

    private val symbol = Symbol.Icon.IconEnum.inbox.toIcon()

    @Test
    fun insert_blankName_throws() = runBlocking {
        initTestDb()
        seedTaskFolders()
        val ex = assertFailsWith<UiException> {
            TaskFolderDb.insertWithValidation(rawName = "  ", activityDb = null, symbol = symbol)
        }
        assertEquals("Invalid folder name", ex.message)
        assertEquals(3, TaskFolderDb.selectAllSorted().size)
    }

    @Test
    fun update_blankName_throws() = runBlocking {
        initTestDb()
        TaskFolderDb.insertNoValidation(2, 1, null, "Inbox", symbol)
        val folderDb = TaskFolderDb.selectAllSorted().first()
        val ex = assertFailsWith<UiException> {
            folderDb.updateWithValidation(sort = 1, activityDb = null, rawName = "   ", symbol = symbol)
        }
        assertEquals("Invalid folder name", ex.message)
        assertEquals("Inbox", TaskFolderDb.selectAllSorted().first().name)
    }

    @Test
    fun insert_duplicateActivity_throws() = runBlocking {
        initTestDb()
        seedTaskFolders()
        val activityDb = insertActivitySq(id = 1, name = "Work")
        TaskFolderDb.insertWithValidation(rawName = "Folder A", activityDb = activityDb, symbol = symbol)
        val ex = assertFailsWith<UiException> {
            TaskFolderDb.insertWithValidation(rawName = "Folder B", activityDb = activityDb, symbol = symbol)
        }
        assertEquals("Work already exists", ex.message)
    }

    @Test
    fun update_duplicateActivity_throws_selfExcluded() = runBlocking {
        initTestDb()
        val activityDb = insertActivitySq(id = 1, name = "Work")
        TaskFolderDb.insertNoValidation(10, 1, activityDb, "Folder A", symbol)
        TaskFolderDb.insertNoValidation(11, 2, null, "Folder B", symbol)
        refreshCache()
        val (folderA, folderB) = TaskFolderDb.selectAllSorted().let { it[0] to it[1] }

        val ex = assertFailsWith<UiException> {
            folderB.updateWithValidation(sort = 2, activityDb = activityDb, rawName = "Folder B", symbol = symbol)
        }
        assertEquals("Work already exists", ex.message)

        // Self excluded: updating folder A with its own activity is fine.
        folderA.updateWithValidation(sort = 1, activityDb = activityDb, rawName = "Folder A!", symbol = symbol)
        assertEquals("Folder A!", TaskFolderDb.selectAllSorted().first { it.id == 10 }.name)
    }

    @Test
    fun insert_idIsTime_sortIsMaxPlusOne() = runBlocking {
        initTestDb()
        seedTaskFolders()
        TaskFolderDb.insertNoValidation(10, 5, null, "Old", symbol)
        TaskFolderDb.insertWithValidation(rawName = "New", activityDb = null, symbol = symbol)
        val newFolderDb = TaskFolderDb.selectAllSorted().first { it.name == "New" }
        assertTrue((newFolderDb.id - time()).absoluteValue <= 2, "id=${newFolderDb.id}")
        assertEquals(6, newFolderDb.sort)
    }

    @Test
    fun selectAllSorted_ordersBySortThenId() = runBlocking {
        initTestDb()
        TaskFolderDb.insertNoValidation(100, 2, null, "B1", symbol)
        TaskFolderDb.insertNoValidation(101, 1, null, "A", symbol)
        TaskFolderDb.insertNoValidation(102, 2, null, "B2", symbol)
        assertEquals(
            listOf(101, 100, 102),
            TaskFolderDb.selectAllSorted().map { it.id },
        )
    }

    @Test
    fun updateSortMany_rewritesContiguousSort() = runBlocking {
        initTestDb()
        TaskFolderDb.insertNoValidation(10, 5, null, "A", symbol)
        TaskFolderDb.insertNoValidation(11, 7, null, "B", symbol)
        TaskFolderDb.insertNoValidation(12, 9, null, "C", symbol)
        val all = TaskFolderDb.selectAllSorted()
        // Reorder: C, A, B
        TaskFolderDb.updateSortMany(listOf(all[2], all[0], all[1]))
        val sorted = TaskFolderDb.selectAllSorted()
        assertEquals(listOf(12, 10, 11), sorted.map { it.id })
        assertEquals(listOf(0, 1, 2), sorted.map { it.sort })
    }
}
