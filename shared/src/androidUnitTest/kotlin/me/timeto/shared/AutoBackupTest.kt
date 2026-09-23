package me.timeto.shared

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.timeto.shared.backups.AutoBackup
import me.timeto.shared.backups.Backup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoBackupTest {

    @Test
    fun buildAutoBackup_typeAndFileName() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = time(), activityId = 1)

        val data = AutoBackup.buildAutoBackup()

        val json = Json.parseToJsonElement(data.jsonString).jsonObject
        assertEquals(JsonPrimitive("autobackup"), json["type"])
        assertEquals(JsonPrimitive(1), json["version"])

        // Empty prefix: filename is exactly the timestamped name
        val expected = Backup.prepFileName(data.unixTime, prefix = "")
        assertEquals(expected, data.fileName)
        // Round-trips through fileNameToUnixTime
        assertEquals(data.unixTime.time, Backup.fileNameToUnixTime(data.fileName).time)
        assertTrue(data.fileName.matches("\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}\\.json".toRegex()))
    }
}
