package me.timeto.shared

import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TaskMiscTest {

    @Test
    fun danglingGoalToken_parsesToNull() {
        // Deleting an activity does not rewrite task texts; the
        // surviving {{goal_<id>}} token must parse to null.
        Cache.activitiesDb = emptyList()
        val tf = "buy milk {{goal_999}}".textFeatures()
        assertNull(tf.activityDb)
        assertEquals("buy milk {{goal_999}}", tf.textNoFeatures)
    }

    @Test
    fun requireTaskFolder_missingFolder_throws() {
        Cache.taskFoldersDbSorted = listOf(
            TaskFolderDb(
                id = TaskFolderDb.ID_TODAY,
                sort = 0,
                activity_id = null,
                name = "Today",
                symbol_raw = Symbol.Icon.IconEnum.inbox.toIcon().raw,
            )
        )
        val taskDb = TaskDb(id = 1, folder_id = 999, text = "x")
        assertFailsWith<NoSuchElementException> {
            Cache.requireTaskFolder(taskDb.folder_id)
        }
    }

    @Test
    fun requireTaskFolder_present_returnsFolder() {
        val folderDb = TaskFolderDb(
            id = TaskFolderDb.ID_TODAY,
            sort = 0,
            activity_id = null,
            name = "Today",
            symbol_raw = Symbol.Icon.IconEnum.inbox.toIcon().raw,
        )
        Cache.taskFoldersDbSorted = listOf(folderDb)
        val taskDb = TaskDb(id = 1, folder_id = TaskFolderDb.ID_TODAY, text = "x")
        assertEquals(folderDb, Cache.requireTaskFolder(taskDb.folder_id))
    }
}
