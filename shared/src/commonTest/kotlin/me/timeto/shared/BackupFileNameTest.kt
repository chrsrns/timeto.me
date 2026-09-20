package me.timeto.shared

import me.timeto.shared.backups.Backup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupFileNameTest {

    @Test
    fun prepFileName_format() {
        val name = Backup.prepFileName(UnixTime(), prefix = "timetome_")
        assertTrue(
            Regex("^timetome_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}\\.json$").matches(name),
            "name=$name",
        )
    }

    @Test
    fun fileNameRoundtrip_recoversTime() {
        val now = UnixTime()
        val name = Backup.prepFileName(now, prefix = "")
        assertEquals(now.time, Backup.fileNameToUnixTime(name).time)
    }

    @Test
    fun fileNameToUnixTime_knownName() {
        val unixTime = Backup.fileNameToUnixTime("timetome_2026_09_20_16_30_15.json")
        assertEquals(2026, unixTime.year())
        assertEquals(9, unixTime.month())
        assertEquals(20, unixTime.dayOfMonth())
        assertEquals(
            "16:30",
            unixTime.getStringByComponents(UnixTime.StringComponent.hhmm24),
        )
    }

    @Test
    fun fileNameToUnixTime_badName_throws() {
        listOf(
            "foo",
            "x.json",
            "2026_9_20_16_30_15.json", // unpadded month
            "2026_09_20_16_30.json",
            ".json",
        ).forEach { name ->
            assertFailsWith<Exception>("name=$name") {
                Backup.fileNameToUnixTime(name)
            }
        }
    }
}
