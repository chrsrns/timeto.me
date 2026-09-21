package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.ShortcutDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShortcutDbTest {

    @Test
    fun insert_blankName_throws() = runBlocking {
        initTestDb()
        val ex = assertFailsWith<UiException> {
            ShortcutDb.insertWithValidation(name = "  ", uri = "https://x")
        }
        assertEquals("Empty name", ex.message)
    }

    @Test
    fun insert_blankUri_throws() = runBlocking {
        initTestDb()
        val ex = assertFailsWith<UiException> {
            ShortcutDb.insertWithValidation(name = "A", uri = "  ")
        }
        assertEquals("Empty shortcut link", ex.message)
    }

    @Test
    fun insert_duplicateNameCaseInsensitive_throws() = runBlocking {
        initTestDb()
        ShortcutDb.insertWithValidation(name = "Abc", uri = "u1")
        val ex = assertFailsWith<UiException> {
            ShortcutDb.insertWithValidation(name = "abc", uri = "u2")
        }
        assertEquals("abc already exists", ex.message)
    }

    @Test
    fun insert_trimsNameAndUri() = runBlocking {
        initTestDb()
        val shortcutDb = ShortcutDb.insertWithValidation(name = "  A  ", uri = "  u  ")
        assertEquals("A", shortcutDb.name)
        assertEquals("u", shortcutDb.uri)
    }

    @Test
    fun update_ownNameAllowed_otherNameThrows() = runBlocking {
        initTestDb()
        val a = ShortcutDb.insertWithValidation(name = "A", uri = "u1")
        ShortcutDb.insertWithValidation(name = "B", uri = "u2")

        val updated = a.updateWithValidation(name = "a", uri = "u1")
        assertEquals("a", updated.name)

        val ex = assertFailsWith<UiException> {
            a.updateWithValidation(name = "b", uri = "u1")
        }
        assertEquals("b already exists", ex.message)
    }

    @Test
    fun insert_idsAreLastPlusOne_firstIsZero() = runBlocking {
        initTestDb()
        val first = ShortcutDb.insertWithValidation(name = "A", uri = "u1")
        val second = ShortcutDb.insertWithValidation(name = "B", uri = "u2")
        assertEquals(0, first.id)
        assertEquals(1, second.id)

        second.delete()
        val third = ShortcutDb.insertWithValidation(name = "C", uri = "u3")
        assertEquals(1, third.id)
    }
}
