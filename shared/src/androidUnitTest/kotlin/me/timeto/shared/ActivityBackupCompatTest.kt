package me.timeto.shared

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.db
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Activity backup tuples are positional. `alarm_mode` is appended at index 14,
 * so backups written before the column existed have 14 items and must restore
 * without throwing.
 */
class ActivityBackupCompatTest {

    private fun activityTuple(
        id: Int = 1,
        name: String = "Work",
        alarmMode: JsonElement? = null,
    ): JsonArray {
        val base = listOf<JsonElement>(
            JsonPrimitive(id),
            JsonNull,
            JsonPrimitive(ActivityDb.Type.general.id),
            JsonPrimitive(name),
            JsonNull,
            JsonPrimitive(ActivityDb.TimerType.TimerPicker.dbValue),
            JsonPrimitive(ActivityDb.Period.Weekly().toJson().toString()),
            JsonPrimitive("icon--inbox"),
            JsonPrimitive("0:0:1"),
            JsonPrimitive("1,2,3,255"),
            JsonPrimitive(0),
            JsonPrimitive(0),
            JsonPrimitive(0),
            JsonPrimitive(""),
        )
        return JsonArray(if (alarmMode == null) base else base + alarmMode)
    }

    @Test
    fun legacy14ItemTuple_restoresWithNullAlarmMode() = runBlocking {
        initTestDb()

        val legacy = activityTuple()
        assertEquals(14, legacy.size)

        ActivityDb.backupable__restore(legacy)

        val restored = ActivityDb.selectByIdOrNull(1)
        assertNotNull(restored)
        assertNull(restored.alarm_mode)
    }

    @Test
    fun alarmModeTuple_roundTrips() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work", alarmMode = 0)

        val tuple = ActivityDb.selectByIdOrNull(1)!!.backupable__backup().jsonArray
        assertEquals(15, tuple.size)
        assertEquals(0, tuple[14].toString().toInt())

        db.activityQueries.deleteAll()
        ActivityDb.backupable__restore(tuple)

        assertEquals(0, ActivityDb.selectByIdOrNull(1)!!.alarm_mode)
    }

    @Test
    fun nullAlarmModeTuple_restoresAsInherit() = runBlocking {
        initTestDb()

        val tuple = activityTuple(alarmMode = JsonNull)
        assertEquals(15, tuple.size)

        ActivityDb.backupable__restore(tuple)

        assertNull(ActivityDb.selectByIdOrNull(1)!!.alarm_mode)
    }
}
