package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.IntervalDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IntervalDbTest {

    @Test
    fun delete_singleInterval_throwsTheOnlyEntry() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        val intervalDb = insertIntervalSq(id = 1, time = 1_000)

        val ex = assertFailsWith<UiException> { intervalDb.delete() }
        assertEquals("The only entry", ex.message)
        assertEquals(1, IntervalDb.selectCount())
    }

    @Test
    fun moveToTasks_singleInterval_throwsTheOnlyEntry() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        val intervalDb = insertIntervalSq(id = 1, time = 1_000)

        val ex = assertFailsWith<UiException> { intervalDb.moveToTasks() }
        assertEquals("The only entry", ex.message)
        assertEquals(1, IntervalDb.selectCount())
    }

    @Test
    fun delete_withTwoIntervals_succeeds() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = 1_000)
        val second = insertIntervalSq(id = 2, time = 2_000)

        second.delete()
        assertEquals(1, IntervalDb.selectCount())
    }

    @Test
    fun selectLastOneOrNull_sameTime_returnsHigherId() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = 5_000)
        insertIntervalSq(id = 2, time = 5_000)

        val last = IntervalDb.selectLastOneOrNull()
        assertEquals(2, last?.id)
        assertEquals(2, IntervalDb.selectDesc(limit = 1).first().id)
        assertTrue(last!!.time == 5_000)
    }
}
