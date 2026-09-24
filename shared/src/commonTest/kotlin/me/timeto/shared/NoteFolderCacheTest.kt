package me.timeto.shared

import me.timeto.shared.db.NoteDb
import me.timeto.shared.db.NoteFolderDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NoteFolderCacheTest {

    @Test
    fun requireNoteFolder_missingFolder_throws() {
        Cache.noteFoldersDb = emptyList()
        val noteDb = NoteDb(id = 1, time = 1, sort = 0, folderId = 99, text = "x")
        assertFailsWith<NoSuchElementException> {
            Cache.requireNoteFolder(noteDb.folderId)
        }
    }

    @Test
    fun requireNoteFolder_presentFolder_returns() {
        val folderDb = NoteFolderDb(
            id = 5, time = 1, sort = 0, onHome = true,
            symbol_raw = "icon--inbox", name = "Notes",
        )
        Cache.noteFoldersDb = listOf(folderDb)
        val noteDb = NoteDb(id = 1, time = 1, sort = 0, folderId = 5, text = "x")
        assertEquals(folderDb, Cache.requireNoteFolder(noteDb.folderId))
        Cache.noteFoldersDb = emptyList()
    }
}
