package me.timeto.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.NoteDb
import me.timeto.shared.db.NoteFolderDb
import me.timeto.shared.vm.note_folder_form.NoteFolderFormVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteFolderFormVmTest {

    private val symbol = Symbol.Icon.IconEnum.inbox.toIcon()

    @Test
    fun delete_folderWithNote_alerts() = runBlocking {
        initTestDb()
        NoteFolderDb.insertNoValidation(id = 1, sort = 0, onHome = true, symbol = symbol, name = "Notes")
        val folderDb = NoteFolderDb.selectAllSorted().first()
        NoteDb.insertWithValidation(text = "note", noteFolderDb = folderDb)

        val dialogs = TestDialogsManager()
        NoteFolderFormVm(folderDb).delete(folderDb, dialogs) {}

        assertEquals(
            "The folder must be empty before deletion",
            withTimeout(5_000) { dialogs.alerts.receive() },
        )
        assertEquals(1, NoteFolderDb.selectAllSorted().size)
    }

    @Test
    fun delete_emptyFolder_confirmsAndDeletes() = runBlocking {
        initTestDb()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            NoteFolderDb.insertNoValidation(id = 1, sort = 0, onHome = true, symbol = symbol, name = "Notes")
            val folderDb = NoteFolderDb.selectAllSorted().first()
            val dialogs = TestDialogsManager()
            val onDelete = CompletableDeferred<Unit>()

            NoteFolderFormVm(folderDb).delete(folderDb, dialogs) {
                onDelete.complete(Unit)
            }

            val (_, onConfirm) = withTimeout(5_000) { dialogs.confirmations.receive() }
            onConfirm()
            withTimeout(5_000) { onDelete.await() }

            assertTrue(NoteFolderDb.selectAllSorted().isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun save_nullSymbol_alertsNoPersist() = runBlocking {
        initTestDb()
        val vm = NoteFolderFormVm(null)
        vm.setName("New Folder")
        val dialogs = TestDialogsManager()

        vm.save(dialogs) {}

        assertEquals("No Symbol", withTimeout(5_000) { dialogs.alerts.receive() })
        assertTrue(NoteFolderDb.selectAllSorted().isEmpty())
    }
}
