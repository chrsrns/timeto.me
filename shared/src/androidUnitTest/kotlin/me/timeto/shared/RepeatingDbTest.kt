package me.timeto.shared

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import me.timeto.shared.db.RepeatingDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db
import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepeatingDbTest {

    private val period = RepeatingDb.Period.EveryNDays(1)

    @Test
    fun insert_idIsMaxOfTimeAndLastPlusOne() = runBlocking {
        initTestDb()

        // Empty table -> id ~ time()
        RepeatingDb.insertWithValidationEx(
            text = "a", period = period, lastDay = 1,
            daytime = null, isImportant = false, inCalendar = true,
        )
        val id1 = RepeatingDb.selectAsc().last().id
        assertTrue((id1 - time()).absoluteValue <= 2, "id1=$id1")

        // Same-second tie -> lastId + 1
        RepeatingDb.insertWithValidationEx(
            text = "b", period = period, lastDay = 1,
            daytime = null, isImportant = false, inCalendar = true,
        )
        val id2 = RepeatingDb.selectAsc().last().id
        assertEquals(id1 + 1, id2)

        // Forced future last id -> lastId + 1 wins over time()
        val futureId = time() + 50
        db.repeatingQueries.insert(
            id = futureId, text = "c", last_day = 1,
            type_id = period.type.id, value_ = period.value,
            daytime = null, is_important = 0, in_calendar = 1,
        )
        RepeatingDb.insertWithValidationEx(
            text = "d", period = period, lastDay = 1,
            daytime = null, isImportant = false, inCalendar = true,
        )
        assertEquals(futureId + 1, RepeatingDb.selectAsc().last().id)
    }

    @Test
    fun syncTodaySafe_materializesOnceAndBumpsLastDay() = runBlocking {
        initTestDb()
        seedTaskFolders()
        refreshCache()

        val today = UnixTime().localDay
        // Missed periods: last_day = today - 3 -> still one materialized task.
        RepeatingDb.insertWithValidationEx(
            text = "med {{goal_1}}", period = period, lastDay = today - 3,
            daytime = null, isImportant = false, inCalendar = true,
        )
        insertActivitySq(id = 1, name = "Health")
        refreshCache()
        val repeatingDb = RepeatingDb.selectAsc().first()

        RepeatingDb.syncTodaySafe(today)

        val tasksDb = TaskDb.selectAsc()
        assertEquals(1, tasksDb.size)
        assertEquals(TaskFolderDb.ID_TODAY, tasksDb.first().folder_id)
        val fromRepeating = tasksDb.first().text.textFeatures().fromRepeating
        assertEquals(repeatingDb.id, fromRepeating?.id)
        assertEquals(today, fromRepeating?.day)
        assertEquals(today, RepeatingDb.selectAsc().first().last_day)
    }

    @Test
    fun syncTodaySafe_secondRunNoDuplicate() = runBlocking {
        initTestDb()
        seedTaskFolders()
        refreshCache()

        val today = UnixTime().localDay
        RepeatingDb.insertWithValidationEx(
            text = "med", period = period, lastDay = today - 1,
            daytime = null, isImportant = false, inCalendar = true,
        )

        RepeatingDb.syncTodaySafe(today)
        RepeatingDb.syncTodaySafe(today)

        assertEquals(1, TaskDb.selectAsc().size)
        assertEquals(today, RepeatingDb.selectAsc().first().last_day)
    }

    @Test
    fun delete_leavesMaterializedTaskWithDanglingToken() = runBlocking {
        initTestDb()
        seedTaskFolders()
        refreshCache()

        val today = UnixTime().localDay
        RepeatingDb.insertWithValidationEx(
            text = "med", period = period, lastDay = today - 1,
            daytime = null, isImportant = false, inCalendar = true,
        )
        val repeatingDb = RepeatingDb.selectAsc().first()
        RepeatingDb.syncTodaySafe(today)

        repeatingDb.delete()

        assertTrue(RepeatingDb.selectAsc().isEmpty())
        val tasksDb = TaskDb.selectAsc()
        assertEquals(1, tasksDb.size)
        // Task keeps the #r token; parses without the repeating row (documented behavior).
        assertEquals(repeatingDb.id, tasksDb.first().text.textFeatures().fromRepeating?.id)
    }

    @Test
    fun backupRestore_tupleRoundtrip() = runBlocking {
        initTestDb()

        RepeatingDb.insertWithValidationEx(
            text = "med", period = period, lastDay = 12345,
            daytime = 3600, isImportant = true, inCalendar = false,
        )
        RepeatingDb.insertWithValidationEx(
            text = "pill", period = RepeatingDb.Period.DaysOfWeek(setOf(0, 6)), lastDay = 12346,
            daytime = null, isImportant = false, inCalendar = true,
        )
        val originals = RepeatingDb.selectAsc()
        assertEquals(2, originals.size)

        // Tuple order: [id, text, last_day, type_id, value, daytime, is_important, in_calendar]
        val backups = originals.map { it.backupable__backup().jsonArray }
        val b1 = backups[0]
        assertEquals(originals[0].id, b1.getInt(0))
        assertEquals("med", b1.getString(1))
        assertEquals(12345, b1.getInt(2))
        assertEquals(period.type.id, b1.getInt(3))
        assertEquals("1", b1.getString(4))
        assertEquals(3600, b1.getInt(5))
        assertEquals(1, b1.getInt(6))
        assertEquals(0, b1.getInt(7))
        assertNull(backups[1].getIntOrNull(5)) // null daytime

        // Roundtrip: delete originals (restore re-inserts original ids), restore, compare.
        originals.forEach { it.delete() }
        assertTrue(RepeatingDb.selectAsc().isEmpty())
        backups.forEach { RepeatingDb.backupable__restore(it) }

        val restored = RepeatingDb.selectAsc()
        assertEquals(originals.size, restored.size)
        originals.zip(restored).forEach { (o, r) ->
            assertEquals(o.id, r.id)
            assertEquals(o.text, r.text)
            assertEquals(o.last_day, r.last_day)
            assertEquals(o.type_id, r.type_id)
            assertEquals(o.value, r.value)
            assertEquals(o.daytime, r.daytime)
            assertEquals(o.is_important, r.is_important)
            assertEquals(o.in_calendar, r.in_calendar)
        }

        // backupable__update writes fields from the same tuple layout.
        val updatedJson = listOf(
            restored[0].id, "med v2", restored[0].last_day, restored[0].type_id,
            restored[0].value, restored[0].daytime, restored[0].is_important,
            restored[0].in_calendar,
        ).toJsonArray()
        restored[0].backupable__update(updatedJson)
        assertEquals("med v2", RepeatingDb.selectAsc().first { it.id == restored[0].id }.text)
    }
}
