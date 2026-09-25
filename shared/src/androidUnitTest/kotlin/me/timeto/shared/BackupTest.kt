package me.timeto.shared

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.timeto.shared.Symbol.Icon.IconEnum
import me.timeto.shared.backups.Backup
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.db.EventDb
import me.timeto.shared.db.EventTemplateDb
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.NoteDb
import me.timeto.shared.db.NoteFolderDb
import me.timeto.shared.db.RepeatingDb
import me.timeto.shared.db.ShortcutDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTest {

    private val backupKeys = listOf(
        "activities", "intervals", "task_folders", "tasks",
        "checklists", "checklist_items", "shortcuts", "repeatings",
        "events", "event_templates", "note_folders", "notes", "kv",
    )

    private suspend fun seedAllTables() {
        insertActivitySq(id = 1, name = "Work")
        insertIntervalSq(id = 1, time = time() - 60, activityId = 1, note = "deep")
        seedTaskFolders()
        TaskDb.insertWithValidation(
            folder = TaskFolderDb.selectAllSorted().first(),
            text = "task one",
        )
        val list = ChecklistDb.insertWithValidation(name = "chk", isResetOnDayStarts = false)
        ChecklistItemDb.insertWithValidation(text = "i", checklist = list, isChecked = true)
        ShortcutDb.insertWithValidation(name = "sc", uri = "https://example.com")
        RepeatingDb.insertWithValidationEx(
            text = "rep", period = RepeatingDb.Period.EveryNDays(1),
            lastDay = 1, daytime = null, isImportant = false, inCalendar = true,
        )
        EventDb.insertWithValidation(text = "evt", localTime = time())
        EventTemplateDb.insertWithValidation(daytime = 3_600, text = "tpl")
        NoteFolderDb.insertNoValidation(
            id = 1, sort = 0, onHome = true,
            symbol = IconEnum.inbox.toIcon(), name = "nf",
        )
        NoteDb.insertWithValidation(
            text = "note",
            noteFolderDb = NoteFolderDb.selectAllSorted().first(),
        )
        KvDb.KEY.TOKEN.upsertString("tok123")
    }

    private suspend fun snapshotAll(): Map<String, List<String>> = mapOf(
        "activities" to ActivityDb.selectAll().map { it.backupable__backup().toString() },
        "intervals" to IntervalDb.selectDesc(Int.MAX_VALUE).map { it.backupable__backup().toString() },
        "task_folders" to TaskFolderDb.selectAllSorted().map { it.backupable__backup().toString() },
        "tasks" to TaskDb.selectAsc().map { it.backupable__backup().toString() },
        "checklists" to ChecklistDb.selectAsc().map { it.backupable__backup().toString() },
        "checklist_items" to ChecklistItemDb.selectSorted().map { it.backupable__backup().toString() },
        "shortcuts" to ShortcutDb.selectAsc().map { it.backupable__backup().toString() },
        "repeatings" to RepeatingDb.selectAsc().map { it.backupable__backup().toString() },
        "events" to EventDb.selectAscByTime().map { it.backupable__backup().toString() },
        "event_templates" to EventTemplateDb.selectAscSorted().map { it.backupable__backup().toString() },
        "note_folders" to NoteFolderDb.selectAllSorted().map { it.backupable__backup().toString() },
        "notes" to NoteDb.selectAllSorted().map { it.backupable__backup().toString() },
        "kv" to KvDb.selectAll().map { it.backupable__backup().toString() },
    )

    private fun backupJsonWith(key: String, value: kotlinx.serialization.json.JsonElement): JsonObject =
        JsonObject(jsonMap + (key to value))

    private lateinit var jsonMap: Map<String, kotlinx.serialization.json.JsonElement>

    @Test
    fun createRestore_allTablesRoundTrip() = runBlocking {
        initTestDb()
        seedAllTables()
        val before = snapshotAll()

        val jString = Backup.create("manual")
        val json = Json.parseToJsonElement(jString).jsonObject

        // Meta + all 13 data keys
        assertEquals(JsonPrimitive(1), json["version"])
        assertEquals(JsonPrimitive("manual"), json["type"])
        backupKeys.forEach { assertNotNull(json[it], "missing key: $it") }

        Backup.restore(jString)

        assertEquals(before, snapshotAll())
    }

    @Test
    fun restore_corruptMidTable_rollsBack() = runBlocking {
        initTestDb()
        seedAllTables()
        val before = snapshotAll()
        jsonMap = Json.parseToJsonElement(Backup.create("manual")).jsonObject

        // "tasks" is restored after activities/intervals/task_folders —
        // their deletes+inserts must roll back too.
        assertFailsWith<Exception> {
            Backup.restore(backupJsonWith("tasks", JsonPrimitive("not-an-array")).toString())
        }
        assertEquals(before, snapshotAll())
    }

    @Test
    fun restore_missingKeyAndWrongTypes_throw() = runBlocking {
        initTestDb()
        seedAllTables()
        val before = snapshotAll()
        jsonMap = Json.parseToJsonElement(Backup.create("manual")).jsonObject

        // Missing data key -> NPE on jsonObject[key]!!
        val missingKey = JsonObject(jsonMap - "notes").toString()
        assertFailsWith<Exception> { Backup.restore(missingKey) }
        assertEquals(before, snapshotAll())

        // Wrong element type inside an array
        val wrongType = backupJsonWith(
            "checklists",
            kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("x"))),
        ).toString()
        assertFailsWith<Exception> { Backup.restore(wrongType) }
        assertEquals(before, snapshotAll())
    }

    @Test
    fun restore_trustsMalformedRows() = runBlocking {
        initTestDb()
        seedTaskFolders()
        val json = buildString {
            append("{")
            backupKeys.forEachIndexed { i, key ->
                val value = when (key) {
                    // Task referencing a non-existent folder — restored as-is.
                    "tasks" -> "[[999,999,\"dangling\"]]"
                    else -> "[]"
                }
                append("\"$key\":$value")
                if (i < backupKeys.lastIndex) append(",")
            }
            append("}")
        }

        Backup.restore(json)

        val tasksDb = TaskDb.selectAsc()
        assertEquals(1, tasksDb.size)
        assertEquals(999, tasksDb.single().folder_id)
        assertEquals("dangling", tasksDb.single().text)
    }

    @Test
    fun restore_ignoresVersionField() = runBlocking {
        initTestDb()
        seedAllTables()
        val before = snapshotAll()
        jsonMap = Json.parseToJsonElement(Backup.create("manual")).jsonObject

        Backup.restore(backupJsonWith("version", JsonPrimitive(999)).toString())

        assertEquals(before, snapshotAll())
    }

    @Test
    fun restore_clearsSnoozeKeys() = runBlocking {
        initTestDb()
        seedAllTables()
        KvDb.KEY.ALARM_SNOOZE_UNTIL.upsertInt(time() + 600)
        KvDb.KEY.ALARM_SNOOZE_INTERVAL_ID.upsertInt(1)

        // The backup was taken mid-snooze, so it carries both keys.
        val jString = Backup.create("manual")
        assertTrue(jString.contains(KvDb.KEY.ALARM_SNOOZE_UNTIL.name))

        Backup.restore(jString)

        assertNull(KvDb.KEY.ALARM_SNOOZE_UNTIL.selectOrNull())
        assertNull(KvDb.KEY.ALARM_SNOOZE_INTERVAL_ID.selectOrNull())
    }

    @Test
    fun create_intervalsLimit() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = time() - 300, activityId = 1, note = "old")
        insertIntervalSq(id = 2, time = time() - 200, activityId = 1, note = "mid")
        insertIntervalSq(id = 3, time = time() - 100, activityId = 1, note = "new")

        val fullJson = Json.parseToJsonElement(Backup.create("manual")).jsonObject
        assertEquals(3, fullJson["intervals"]!!.jsonArray.size)

        val limitedJson = Json.parseToJsonElement(Backup.create("manual", intervalsLimit = 1)).jsonObject
        val intervals = limitedJson["intervals"]!!.jsonArray
        assertEquals(1, intervals.size)
        // selectDesc keeps the newest interval
        assertEquals(3, intervals.single().jsonArray[0].toString().toInt())
    }
}
