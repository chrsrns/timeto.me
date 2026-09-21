package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import me.timeto.shared.db.NoteDb
import me.timeto.shared.db.NoteFolderDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db
import me.timeto.shared.vm.home.bar.HomeBarAnimate
import me.timeto.shared.vm.home.tasks.HomeTaskStaNoteFolderUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskStaNoteFolderTest {

    private val symbol = Symbol.Icon.IconEnum.inbox.toIcon()

    @Test
    fun onTap_convertsTaskToNote() = runBlocking {
        initTestDb()
        TaskFolderDb.insertNoValidation(TaskFolderDb.ID_TODAY, 0, null, "Today", symbol)
        NoteFolderDb.insertNoValidation(id = 9, sort = 0, onHome = false, symbol = symbol, name = "Notes")
        refreshCache()
        TaskDb.insertWithValidation(
            text = "buy milk",
            folder = Cache.taskFoldersDbSorted.first { it.isToday },
        )
        val taskDb = TaskDb.selectAsc().first()
        val noteFolderDb = NoteFolderDb.selectAllSorted().first { it.id == 9 }

        val events = mutableListOf<HomeBarAnimate>()
        val collectJob = launch {
            HomeBarAnimate.flow.collect { events.add(it) }
        }
        yield()

        val dialogs = TestDialogsManager()
        HomeTaskStaNoteFolderUi(taskDb, NoteFolderUi(noteFolderDb)).onTap(dialogs)
        withTimeout(10_000) {
            while (TaskDb.selectAsc().isNotEmpty() || events.isEmpty())
                delay(25)
        }

        val noteDb = NoteDb.selectAllSorted().single()
        assertEquals("buy milk", noteDb.text)
        assertEquals(9, noteDb.folderId)
        assertEquals(
            listOf<HomeBarAnimate>(HomeBarAnimate.NoteFolder(noteFolderId = 9)),
            events,
        )
        assertTrue(dialogs.alerts.isEmpty)
        collectJob.cancel()
    }

    @Test
    fun onTap_emptyTaskText_alertsAndKeepsTask() = runBlocking {
        initTestDb()
        TaskFolderDb.insertNoValidation(TaskFolderDb.ID_TODAY, 0, null, "Today", symbol)
        NoteFolderDb.insertNoValidation(id = 9, sort = 0, onHome = false, symbol = symbol, name = "Notes")
        refreshCache()
        db.taskQueries.insert(
            id = time() + 1,
            folder_id = TaskFolderDb.ID_TODAY,
            text = "",
        )
        val taskDb = TaskDb.selectAsc().first()
        val noteFolderDb = NoteFolderDb.selectAllSorted().first { it.id == 9 }

        val dialogs = TestDialogsManager()
        HomeTaskStaNoteFolderUi(taskDb, NoteFolderUi(noteFolderDb)).onTap(dialogs)

        assertEquals("Empty text", withTimeout(10_000) { dialogs.alerts.receive() })
        assertTrue(NoteDb.selectAllSorted().isEmpty())
        assertEquals(1, TaskDb.selectAsc().size)
    }
}
