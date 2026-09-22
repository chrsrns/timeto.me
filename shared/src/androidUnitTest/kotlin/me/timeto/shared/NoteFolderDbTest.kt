package me.timeto.shared

import kotlinx.coroutines.runBlocking
import dbsq.NoteFolderSq
import kotlinx.serialization.json.jsonArray
import me.timeto.shared.db.NoteFolderDb
import me.timeto.shared.db.db
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NoteFolderDbTest {

    private val symbol = Symbol.Icon.IconEnum.inbox.toIcon()

    @Test
    fun insert_blankName_throws() = runBlocking {
        initTestDb()
        val ex = assertFailsWith<UiException> {
            NoteFolderDb.insertWithValidation(onHome = true, symbol = symbol, rawName = "   ")
        }
        assertEquals("Invalid folder name", ex.message)
        assertTrue(NoteFolderDb.selectAllSorted().isEmpty())
    }

    @Test
    fun update_blankName_throws() = runBlocking {
        initTestDb()
        NoteFolderDb.insertNoValidation(id = 1, sort = 0, onHome = true, symbol = symbol, name = "Notes")
        val folderDb = NoteFolderDb.selectAllSorted().first()
        val ex = assertFailsWith<UiException> {
            folderDb.updateWithValidation(onHome = true, symbol = symbol, rawName = "  ")
        }
        assertEquals("Invalid folder name", ex.message)
        assertEquals("Notes", NoteFolderDb.selectAllSorted().first().name)
    }

    @Test
    fun duplicateName_caseSensitive_selfExcluded() = runBlocking {
        initTestDb()
        NoteFolderDb.insertWithValidation(onHome = true, symbol = symbol, rawName = "Work")
        NoteFolderDb.insertWithValidation(onHome = true, symbol = symbol, rawName = "Inbox")

        // Exact duplicate rejected
        val ex = assertFailsWith<UiException> {
            NoteFolderDb.insertWithValidation(onHome = true, symbol = symbol, rawName = "Work")
        }
        assertEquals("Work already exists", ex.message)

        // Case-sensitive: lowercase is a different name (unlike checklist folders)
        NoteFolderDb.insertWithValidation(onHome = true, symbol = symbol, rawName = "work")
        assertTrue(NoteFolderDb.selectAllSorted().any { it.name == "work" })

        // Update to another folder's name rejected
        val inbox = NoteFolderDb.selectAllSorted().first { it.name == "Inbox" }
        val ex2 = assertFailsWith<UiException> {
            inbox.updateWithValidation(onHome = true, symbol = symbol, rawName = "Work")
        }
        assertEquals("Work already exists", ex2.message)

        // Self-excluded: updating "Work" with its own name succeeds
        val work = NoteFolderDb.selectAllSorted().first { it.name == "Work" }
        work.updateWithValidation(onHome = false, symbol = symbol, rawName = "Work")
        assertEquals("Work", NoteFolderDb.selectAllSorted().first { it.id == work.id }.name)
    }

    @Test
    fun insert_idAutoIncrement_sortZero_insertionOrder() = runBlocking {
        initTestDb()
        NoteFolderDb.insertWithValidation(onHome = true, symbol = symbol, rawName = "a")
        NoteFolderDb.insertWithValidation(onHome = true, symbol = symbol, rawName = "b")
        val foldersDb = NoteFolderDb.selectAllSorted()
        assertEquals(listOf("a", "b"), foldersDb.map { it.name })
        assertEquals(listOf(1, 2), foldersDb.map { it.id })
        assertTrue(foldersDb.all { it.sort == 0 })
    }

    @Test
    fun update_preservesTimeAndSort() = runBlocking {
        initTestDb()
        db.noteFolderQueries.insertWithId(
            NoteFolderSq(
                id = 1, time = 999, sort = 7, on_home = 1,
                symbol_raw = symbol.raw, name = "Notes",
            )
        )
        val folderDb = NoteFolderDb.selectAllSorted().first()
        folderDb.updateWithValidation(onHome = false, symbol = symbol, rawName = "Renamed")
        val updated = NoteFolderDb.selectAllSorted().first()
        assertEquals(999, updated.time)
        assertEquals(7, updated.sort)
        assertEquals("Renamed", updated.name)
        assertEquals(false, updated.onHome)
    }

    @Test
    fun backupRestore_tupleRoundtrip() = runBlocking {
        initTestDb()
        db.noteFolderQueries.insertWithId(
            NoteFolderSq(
                id = 1, time = 555, sort = 3, on_home = 1,
                symbol_raw = "icon--sun", name = "Home",
            )
        )
        val original = NoteFolderDb.selectAllSorted().first()

        // Tuple order: [id, time, sort, onHome.toInt10(), symbol_raw, name]
        val j = original.backupable__backup().jsonArray
        assertEquals(1, j.getInt(0))
        assertEquals(555, j.getInt(1))
        assertEquals(3, j.getInt(2))
        assertEquals(1, j.getInt(3))
        assertEquals("icon--sun", j.getString(4))
        assertEquals("Home", j.getString(5))

        original.delete()
        assertTrue(NoteFolderDb.selectAllSorted().isEmpty())
        NoteFolderDb.backupable__restore(original.backupable__backup())
        val restored = NoteFolderDb.selectAllSorted().first()
        assertEquals(original.id, restored.id)
        assertEquals(original.time, restored.time)
        assertEquals(original.sort, restored.sort)
        assertEquals(original.onHome, restored.onHome)
        assertEquals(original.symbol_raw, restored.symbol_raw)
        assertEquals(original.name, restored.name)
    }
}
