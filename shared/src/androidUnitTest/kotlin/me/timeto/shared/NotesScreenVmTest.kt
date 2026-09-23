package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.NoteFolderDb
import me.timeto.shared.vm.notes.NotesScreenVm
import kotlin.test.Test
import kotlin.test.assertEquals

class NotesScreenVmTest {

    private val symbol = Symbol.Icon.IconEnum.inbox.toIcon()

    @Test
    fun survivesFolderDeletion_keepsLastFolder() = runBlocking {
        initTestDb()
        NoteFolderDb.insertNoValidation(id = 1, sort = 0, onHome = true, symbol = symbol, name = "Notes")
        val folderDb = NoteFolderDb.selectAllSorted().first()

        val vm = NotesScreenVm(folderDb)
        try {
            folderDb.delete()

            // Wait for the delete to hit the db, then give the flow time to emit
            // the folder-less list. The VM must keep the last folderDb (no crash).
            withTimeout(5_000) {
                while (NoteFolderDb.selectAllSorted().isNotEmpty())
                    delay(50)
            }
            delay(300)

            assertEquals(1, vm.state.value.noteFolderDb.id)
            assertEquals("Notes", vm.state.value.title)
        } finally {
            vm.onDestroy()
        }
    }
}
