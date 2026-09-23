package me.timeto.shared

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.timeto.shared.backups.Backup
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.db.EventDb
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.NoteDb
import me.timeto.shared.db.RepeatingDb
import me.timeto.shared.db.ShortcutDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Watch sync payload is a Backup json whose "type" carries the sync id
// (iOS sends Backup.create("$timeMls", intervalsLimit = 1)).
class SmartRestoreTest {

    private suspend fun seedSourceWithAll(): String {
        initTestDb()
        insertActivitySq(id = 1, name = "Work")
        insertIntervalSq(id = 1, time = 1_000, activityId = 1, note = "orig")
        seedTaskFolders()
        TaskDb.insertWithValidation("t1", TaskFolderDb.selectAllSorted().first())
        val list = ChecklistDb.insertWithValidation(name = "c", isResetOnDayStarts = false)
        ChecklistItemDb.insertWithValidation("i", list, isChecked = false)
        ShortcutDb.insertWithValidation(name = "s", uri = "https://example.com")
        return Backup.create("2000")
    }

    @Test
    fun restore_appliesSevenTables() = runBlocking {
        val payload = seedSourceWithAll()

        initTestDb()
        SmartRestore.resetLastSyncIdForTesting()
        SmartRestore.restore(payload)

        assertEquals(1, ActivityDb.selectAll().size)
        assertEquals(1, IntervalDb.selectDesc(Int.MAX_VALUE).size)
        assertEquals(3, TaskFolderDb.selectAllSorted().size)
        assertEquals(1, TaskDb.selectAsc().size)
        assertEquals(1, ChecklistDb.selectAsc().size)
        assertEquals(1, ChecklistItemDb.selectSorted().size)
        assertEquals(1, ShortcutDb.selectAsc().size)
    }

    @Test
    fun restore_intervalsDoNotUpdate() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = 1_000, activityId = 1, note = "orig")
        insertIntervalSq(id = 3, time = 3_000, activityId = 1, note = "new")
        val payload = Backup.create("2000")

        initTestDb()
        SmartRestore.resetLastSyncIdForTesting()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = 1_000, activityId = 1, note = "changed")
        insertIntervalSq(id = 2, time = 2_000, activityId = 1, note = "extra")

        SmartRestore.restore(payload)

        val intervals = IntervalDb.selectDesc(Int.MAX_VALUE)
        assertEquals(setOf(1, 3), intervals.map { it.id }.toSet())
        // id=1 kept local content (doNotUpdate), id=2 deleted, id=3 inserted
        assertEquals("changed", intervals.first { it.id == 1 }.note)
    }

    @Test
    fun restore_staleSyncIdIgnored() = runBlocking {
        initTestDb()
        SmartRestore.resetLastSyncIdForTesting()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = 1_000, activityId = 1, note = "v2")
        val fresh = Backup.create("2000")
        val stale = Backup.create("1000") // same data, older id

        SmartRestore.restore(fresh)
        insertIntervalSq(id = 9, time = 9_000, activityId = 1, note = "mut")
        SmartRestore.restore(stale) // must be ignored — would delete id=9
        assertEquals(setOf(1, 9), IntervalDb.selectDesc(Int.MAX_VALUE).map { it.id }.toSet())

        // Equal id is not stale (guard is strict `>`) — reapplies
        SmartRestore.restore(fresh)
        assertEquals(setOf(1), IntervalDb.selectDesc(Int.MAX_VALUE).map { it.id }.toSet())
    }

    @Test
    fun restore_ignoresNonSyncedTables() = runBlocking {
        initTestDb()
        SmartRestore.resetLastSyncIdForTesting()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = 1_000, activityId = 1, note = "n")
        RepeatingDb.insertWithValidationEx(
            text = "rep", period = RepeatingDb.Period.EveryNDays(1),
            lastDay = 1, daytime = null, isImportant = false, inCalendar = true,
        )
        EventDb.insertWithValidation(text = "evt", localTime = time())
        KvDb.KEY.TOKEN.upsertString("tok")
        val payload = Backup.create("2000")

        // Sanity: the payload really does carry the non-synced tables
        val json = Json.parseToJsonElement(payload).jsonObject
        assertEquals(1, json["repeatings"]!!.jsonArray.size)
        assertEquals(1, json["events"]!!.jsonArray.size)
        assertTrue(json["kv"]!!.jsonArray.isNotEmpty())

        initTestDb()
        SmartRestore.resetLastSyncIdForTesting()
        SmartRestore.restore(payload)

        // Only the seven synced tables are applied; the rest stay untouched.
        assertTrue(RepeatingDb.selectAsc().isEmpty())
        assertTrue(EventDb.selectAscByTime().isEmpty())
        assertTrue(NoteDb.selectAllSorted().isEmpty())
        assertNull(KvDb.KEY.TOKEN.selectStringOrNull())
    }
}
