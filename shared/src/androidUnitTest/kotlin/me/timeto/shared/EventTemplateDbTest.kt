package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.EventTemplateDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EventTemplateDbTest {

    @Test
    fun insert_negativeDaytime_throws() = runBlocking {
        initTestDb()
        val ex = assertFailsWith<UiException> {
            EventTemplateDb.insertWithValidation(daytime = -1, text = "t")
        }
        assertEquals("Invalid daytime", ex.message)
        assertTrue(EventTemplateDb.selectAscSorted().isEmpty())
    }

    @Test
    fun update_negativeDaytime_throws() = runBlocking {
        initTestDb()
        EventTemplateDb.insertWithValidation(daytime = 0, text = "t")
        val templateDb = EventTemplateDb.selectAscSorted().single()
        val ex = assertFailsWith<UiException> {
            templateDb.updateWithValidation(daytime = -5, text = "t")
        }
        assertEquals("Invalid daytime", ex.message)
        assertEquals(0, EventTemplateDb.selectAscSorted().single().daytime)
    }

    @Test
    fun insertUpdate_duplicateText_throws_selfExcluded() = runBlocking {
        initTestDb()
        EventTemplateDb.insertWithValidation(daytime = 0, text = "t1")
        EventTemplateDb.insertWithValidation(daytime = 0, text = "t2")

        // Duplicate on insert (whitespace-trimmed)
        val exInsert = assertFailsWith<UiException> {
            EventTemplateDb.insertWithValidation(daytime = 0, text = "  t1  ")
        }
        assertEquals("Template \"t1\" already exists", exInsert.message)

        // Duplicate on update to another template's text
        val t2 = EventTemplateDb.selectAscSorted().first { it.text == "t2" }
        val exUpdate = assertFailsWith<UiException> {
            t2.updateWithValidation(daytime = 0, text = "t1")
        }
        assertEquals("Template \"t1\" already exists", exUpdate.message)

        // Self-update keeps own text
        val t1 = EventTemplateDb.selectAscSorted().first { it.text == "t1" }
        t1.updateWithValidation(daytime = 3_600, text = "t1")
        assertEquals(3_600, EventTemplateDb.selectAscSorted().first { it.id == t1.id }.daytime)
    }
}
