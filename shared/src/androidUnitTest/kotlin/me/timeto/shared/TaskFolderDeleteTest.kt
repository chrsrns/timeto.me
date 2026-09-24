package me.timeto.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.vm.task_folder.TaskFolderFormVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskFolderDeleteTest {

    private val symbol = Symbol.Icon.IconEnum.inbox.toIcon()

    @Test
    fun delete_folderWithTasks_alerts() = runBlocking {
        initTestDb()
        TaskFolderDb.insertNoValidation(2, 1, null, "Inbox", symbol)
        TaskDb.insertWithValidation(
            text = "t",
            folder = TaskFolderDb(2, 1, null, "Inbox", symbol.raw),
        )
        refreshCache()
        val folderDb = Cache.requireTaskFolder(2)
        val dialogs = TestDialogsManager()

        TaskFolderFormVm(folderDb).delete(folderDb, dialogs, onDelete = {})

        assertEquals(
            "The folder must be empty before deletion",
            withTimeout(10_000) { dialogs.alerts.receive() },
        )
        assertTrue(TaskFolderDb.selectAllSorted().any { it.id == 2 })
    }

    @Test
    fun delete_fixedFolders_alert() = runBlocking {
        initTestDb()
        TaskFolderDb.insertNoValidation(TaskFolderDb.ID_TODAY, 0, null, "Today", symbol)
        TaskFolderDb.insertNoValidation(TaskFolderDb.ID_TOMORROW, 1, null, "Tomorrow", symbol)
        TaskFolderDb.insertNoValidation(TaskFolderDb.ID_SOMEDAY, 2, null, "Someday", symbol)
        refreshCache()
        val expected = mapOf(
            TaskFolderDb.ID_TODAY to "It's impossible to delete \"Today\" folder",
            TaskFolderDb.ID_TOMORROW to "It's impossible to delete \"Tomorrow\" folder",
            TaskFolderDb.ID_SOMEDAY to "It's impossible to delete \"Someday\" folder",
        )

        expected.forEach { (id, message) ->
            val folderDb = Cache.requireTaskFolder(id)
            val dialogs = TestDialogsManager()
            TaskFolderFormVm(folderDb).delete(folderDb, dialogs, onDelete = {})
            assertEquals(message, withTimeout(10_000) { dialogs.alerts.receive() }, "id=$id")
        }
        assertEquals(3, TaskFolderDb.selectAllSorted().size)
    }

    @Test
    fun delete_emptyFolder_confirmsAndDeletes() = runBlocking {
        initTestDb()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            TaskFolderDb.insertNoValidation(2, 1, null, "Inbox", symbol)
            refreshCache()
            val folderDb = Cache.requireTaskFolder(2)
            val dialogs = TestDialogsManager()
            val onDelete = CompletableDeferred<Unit>()

            TaskFolderFormVm(folderDb).delete(folderDb, dialogs) {
                onDelete.complete(Unit)
            }

            val (_, onConfirm) = withTimeout(10_000) { dialogs.confirmations.receive() }
            onConfirm()
            withTimeout(10_000) { onDelete.await() }

            assertTrue(TaskFolderDb.selectAllSorted().isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }
}
