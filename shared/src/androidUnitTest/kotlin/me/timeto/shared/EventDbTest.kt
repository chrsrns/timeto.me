package me.timeto.shared

import dbsq.EventSQ
import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.EventDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db
import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EventDbTest {

    @Test
    fun insert_utcRoundTrip() = runBlocking {
        initTestDb()
        val localTime = UnixTime().localDayStartTime() + (10 * 3_600)
        EventDb.insertWithValidation(text = "meet", localTime = localTime)

        val eventDb = EventDb.selectAscByTime().single()
        assertEquals(localTime + localUtcOffset, eventDb.utc_time)
        assertEquals(localTime, eventDb.getLocalTime().time)
    }

    @Test
    fun insertUpdate_blankText_throws() = runBlocking {
        initTestDb()
        val exInsert = assertFailsWith<UiException> {
            EventDb.insertWithValidation(text = "  ", localTime = time())
        }
        assertEquals("Empty text", exInsert.message)
        assertTrue(EventDb.selectAscByTime().isEmpty())

        EventDb.insertWithValidation(text = "meet", localTime = time())
        val eventDb = EventDb.selectAscByTime().single()
        val exUpdate = assertFailsWith<UiException> {
            eventDb.updateWithValidation(text = "", localTime = time())
        }
        assertEquals("Empty text", exUpdate.message)
        assertEquals("meet", EventDb.selectAscByTime().single().text)
    }

    @Test
    fun insert_idCollisionSafe() = runBlocking {
        initTestDb()

        // Empty table -> id ~ time()
        EventDb.insertWithValidation(text = "a", localTime = time())
        val id1 = EventDb.selectAscByTime().single().id
        assertTrue((id1 - time()).absoluteValue <= 2, "id1=$id1")

        // Same-second insert -> lastId + 1 (no PRIMARY KEY collision)
        EventDb.insertWithValidation(text = "b", localTime = time())
        val ids = EventDb.selectAscByTime().map { it.id }
        assertEquals(2, ids.size)
        assertEquals(id1 + 1, ids.max())

        // Forced future id -> lastId + 1 wins over time()
        val futureId = time() + 50
        db.eventQueries.insertObject(
            EventSQ(id = futureId, text = "c", utc_time = time())
        )
        EventDb.insertWithValidation(text = "d", localTime = time())
        assertEquals(futureId + 1, EventDb.selectAscByTime().maxOf { it.id })
    }

    @Test
    fun syncTodaySafe_promotesSortsDeletes_idempotent() = runBlocking {
        initTestDb()
        seedTaskFolders()
        refreshCache()
        val today = UnixTime().localDay

        EventDb.insertWithValidation(
            text = "e09",
            localTime = UnixTime.byLocalDay(today).inSeconds(9 * 3_600).time,
        )
        EventDb.insertWithValidation(
            text = "e12",
            localTime = UnixTime.byLocalDay(today).inSeconds(12 * 3_600).time,
        )
        // All-day marker: utc_time % 86400 == 0 -> sorted last.
        // localTime = today*86400 - offset gives utc_time = today*86400 and localDay = today.
        EventDb.insertWithValidation(
            text = "e00",
            localTime = UnixTime.byLocalDay(today).time,
        )
        EventDb.insertWithValidation(
            text = "tmrw",
            localTime = UnixTime.byLocalDay(today + 1).inSeconds(3_600).time,
        )

        val e00 = EventDb.selectAscByTime().first { it.text == "e00" }
        assertEquals(0, e00.utc_time % 86_400)
        assertEquals(today, e00.getLocalTime().localDay)

        EventDb.syncTodaySafe(today)

        val tasksDb = TaskDb.selectAsc()
        assertEquals(
            listOf("e09", "e12", "e00"),
            tasksDb.map { it.text.textFeatures().textNoFeatures },
        )
        assertTrue(tasksDb.all { it.folder_id == TaskFolderDb.ID_TODAY })
        assertTrue(tasksDb.all { it.text.textFeatures().fromEvent != null })
        // Only tomorrow's event remains in the table.
        assertEquals(listOf("tmrw"), EventDb.selectAscByTime().map { it.text })

        // Second call must not duplicate tasks.
        EventDb.syncTodaySafe(today)
        assertEquals(3, TaskDb.selectAsc().size)
    }
}
