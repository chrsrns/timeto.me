package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TaskDbTest {

    private val todayFolderDb = TaskFolderDb(
        id = TaskFolderDb.ID_TODAY,
        sort = 0,
        activity_id = null,
        name = "Today",
        symbol_raw = Symbol.Icon.IconEnum.inbox.toIcon().raw,
    )

    @Test
    fun insert_emptyText_throws() = runBlocking {
        initTestDb()
        listOf("", "   ").forEach { text ->
            val ex = assertFailsWith<UiException>("text=$text") {
                TaskDb.insertWithValidation(text = text, folder = todayFolderDb)
            }
            assertEquals("Empty text", ex.message)
        }
        assertTrue(TaskDb.selectAsc().isEmpty())
    }

    @Test
    fun update_emptyText_throws() = runBlocking {
        initTestDb()
        TaskDb.insertWithValidation(text = "original", folder = todayFolderDb)
        val taskDb = TaskDb.selectAsc().first()
        val ex = assertFailsWith<UiException> {
            taskDb.updateTextWithValidation("   ")
        }
        assertEquals("Empty text", ex.message)
        assertEquals("original", TaskDb.selectAsc().first().text)
    }

    @Test
    fun insert_nextId_isMaxOfTimeAndLastPlusOne() = runBlocking {
        initTestDb()
        val id1 = db.transactionWithResult {
            TaskDb.insertWithValidation_transactionRequired(todayFolderDb, "t1")
        }
        val id2 = db.transactionWithResult {
            TaskDb.insertWithValidation_transactionRequired(todayFolderDb, "t2")
        }
        assertTrue(id2 > id1, "id1=$id1 id2=$id2")
    }

    @Test
    fun insert_nextId_followsLastIdWhenAheadOfTime() = runBlocking {
        initTestDb()
        val futureId = time() + 5000
        db.taskQueries.insert(id = futureId, folder_id = todayFolderDb.id, text = "x")
        val newId = db.transactionWithResult {
            TaskDb.insertWithValidation_transactionRequired(todayFolderDb, "t")
        }
        assertEquals(futureId + 1, newId)
    }

    @Test
    fun updateFolder_tomorrow_reIdsAndRewritesActivity() = runBlocking {
        initTestDb()
        val activityDb = insertActivitySq(id = 1, name = "Work")
        val symbol = Symbol.Icon.IconEnum.inbox.toIcon()
        TaskFolderDb.insertNoValidation(TaskFolderDb.ID_TODAY, 0, null, "Today", symbol)
        TaskFolderDb.insertNoValidation(TaskFolderDb.ID_TOMORROW, 1, activityDb, "Tomorrow", symbol)
        refreshCache()
        TaskDb.insertWithValidation("do thing", folder = todayFolderDb)
        val taskDb = TaskDb.selectAsc().first()
        val tomorrowFolderDb = Cache.taskFoldersDbSorted.first { it.isTomorrow }

        taskDb.updateFolder(
            taskFolderDb = tomorrowFolderDb,
            updateFolderActivity = true,
            replaceIfTmrw = true,
        )

        val updated = TaskDb.selectAsc().first()
        assertEquals(TaskFolderDb.ID_TOMORROW, updated.folder_id)
        assertNotEquals(taskDb.id, updated.id)
        assertTrue(updated.id > taskDb.id)
        assertTrue("{{goal_1}}" in updated.text, "text=${updated.text}")
    }

    @Test
    fun updateFolder_noActivityRewrite_keepsText() = runBlocking {
        initTestDb()
        val activityDb = insertActivitySq(id = 1, name = "Work")
        val symbol = Symbol.Icon.IconEnum.inbox.toIcon()
        TaskFolderDb.insertNoValidation(TaskFolderDb.ID_TOMORROW, 1, activityDb, "Tomorrow", symbol)
        refreshCache()
        TaskDb.insertWithValidation("do thing", folder = todayFolderDb)
        val taskDb = TaskDb.selectAsc().first()

        taskDb.updateFolder(
            taskFolderDb = Cache.taskFoldersDbSorted.first { it.isTomorrow },
            updateFolderActivity = false,
            replaceIfTmrw = true,
        )

        val updated = TaskDb.selectAsc().first()
        assertEquals("do thing", updated.text)
    }

    @Test
    fun updateFolder_notTomorrow_keepsId() = runBlocking {
        initTestDb()
        val symbol = Symbol.Icon.IconEnum.inbox.toIcon()
        TaskFolderDb.insertNoValidation(2, 1, null, "Inbox", symbol)
        refreshCache()
        TaskDb.insertWithValidation("do thing", folder = todayFolderDb)
        val taskDb = TaskDb.selectAsc().first()

        taskDb.updateFolder(
            taskFolderDb = Cache.requireTaskFolder(2),
            updateFolderActivity = false,
            replaceIfTmrw = true,
        )

        val updated = TaskDb.selectAsc().first()
        assertEquals(taskDb.id, updated.id)
        assertEquals(2, updated.folder_id)
    }
}
