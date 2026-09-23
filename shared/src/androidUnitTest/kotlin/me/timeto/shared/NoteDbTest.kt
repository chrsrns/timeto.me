package me.timeto.shared

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import me.timeto.shared.db.NoteDb
import me.timeto.shared.db.NoteFolderDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NoteDbTest {

    private val symbol = Symbol.Icon.IconEnum.inbox.toIcon()

    @Test
    fun insert_blankText_throws() = runBlocking {
        initTestDb()
        val folderDb = seedFolder()
        val ex = assertFailsWith<UiException> {
            NoteDb.insertWithValidation(text = "   ", noteFolderDb = folderDb)
        }
        assertEquals("Empty text", ex.message)
        assertTrue(NoteDb.selectAllSorted().isEmpty())
    }

    @Test
    fun update_blankText_throws() = runBlocking {
        initTestDb()
        val folderDb = seedFolder()
        NoteDb.insertWithValidation(text = "note", noteFolderDb = folderDb)
        val noteDb = NoteDb.selectAllSorted().first()
        val ex = assertFailsWith<UiException> {
            noteDb.updateWithValidation(newText = "  ", newNoteFolderDb = folderDb)
        }
        assertEquals("Empty text", ex.message)
        assertEquals("note", NoteDb.selectAllSorted().first().text)
    }

    @Test
    fun insert_sortZeroIdAutoInsertionOrder() = runBlocking {
        initTestDb()
        val folderDb = seedFolder()
        NoteDb.insertWithValidation(text = "a", noteFolderDb = folderDb)
        NoteDb.insertWithValidation(text = "b", noteFolderDb = folderDb)
        NoteDb.insertWithValidation(text = "c", noteFolderDb = folderDb)
        val notesDb = NoteDb.selectAllSorted()
        assertEquals(listOf("a", "b", "c"), notesDb.map { it.text })
        assertEquals(notesDb.map { it.id }, notesDb.map { it.id }.sorted()) // insertion order
        assertTrue(notesDb.all { it.sort == 0 })
    }

    @Test
    fun backupRestore_tupleRoundtrip() = runBlocking {
        initTestDb()
        val folderDb = seedFolder()
        NoteDb.insertWithValidation(text = "note body", noteFolderDb = folderDb)
        val original = NoteDb.selectAllSorted().first()

        // Tuple order: [id, time, sort, folderId, text]
        val j = original.backupable__backup().jsonArray
        assertEquals(original.id, j.getInt(0))
        assertEquals(original.time, j.getInt(1))
        assertEquals(original.sort, j.getInt(2))
        assertEquals(original.folderId, j.getInt(3))
        assertEquals("note body", j.getString(4))

        // Roundtrip: delete original (restore re-inserts original id), restore, compare.
        original.delete()
        assertTrue(NoteDb.selectAllSorted().isEmpty())
        NoteDb.backupable__restore(original.backupable__backup())
        val restored = NoteDb.selectAllSorted().first()
        assertEquals(original.id, restored.id)
        assertEquals(original.time, restored.time)
        assertEquals(original.sort, restored.sort)
        assertEquals(original.folderId, restored.folderId)
        assertEquals(original.text, restored.text)

        // backupable__update writes fields from the same tuple layout.
        val updatedJson = listOf(
            restored.id, restored.time, restored.sort, restored.folderId, "updated",
        ).toJsonArray()
        restored.backupable__update(updatedJson)
        assertEquals("updated", NoteDb.selectAllSorted().first().text)
    }

    private suspend fun seedFolder(): NoteFolderDb {
        NoteFolderDb.insertNoValidation(
            id = 1, sort = 0, onHome = true, symbol = symbol, name = "Notes",
        )
        return NoteFolderDb.selectAllSorted().first()
    }
}
