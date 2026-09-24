package me.timeto.shared

import me.timeto.shared.db.KvDb
import me.timeto.shared.db.RepeatingDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayStartOffsetTest {

    @Test
    fun calcDay_zeroOffset_matchesRawLocalDay() {
        val d = UnixTime().localDay
        val start = UnixTime.byLocalDay(d).localDayStartTime()
        assertEquals(d, DayStartOffsetUtils.calcDay(time = start, dayStartOffsetSeconds = 0))
        assertEquals(d, DayStartOffsetUtils.calcDay(time = start + 86_399, dayStartOffsetSeconds = 0))
        assertEquals(d - 1, DayStartOffsetUtils.calcDay(time = start - 1, dayStartOffsetSeconds = 0))
    }

    @Test
    fun calcDay_positiveOffset_shiftsBoundaryEarlier() {
        // Day "starts" at 01:00 local: midnight..01:00 counts as previous day.
        val d = UnixTime().localDay
        val start = UnixTime.byLocalDay(d).localDayStartTime()
        val offset = 3_600
        assertEquals(d - 1, DayStartOffsetUtils.calcDay(time = start, dayStartOffsetSeconds = offset))
        assertEquals(d - 1, DayStartOffsetUtils.calcDay(time = start + offset - 1, dayStartOffsetSeconds = offset))
        assertEquals(d, DayStartOffsetUtils.calcDay(time = start + offset, dayStartOffsetSeconds = offset))
    }

    @Test
    fun calcDay_negativeOffset_shiftsBoundaryLater() {
        // Day "starts" at 23:00 of the previous calendar day.
        val d = UnixTime().localDay
        val start = UnixTime.byLocalDay(d).localDayStartTime()
        val offset = -3_600
        assertEquals(d, DayStartOffsetUtils.calcDay(time = start - 3_600, dayStartOffsetSeconds = offset))
        assertEquals(d - 1, DayStartOffsetUtils.calcDay(time = start - 3_601, dayStartOffsetSeconds = offset))
        assertEquals(d, DayStartOffsetUtils.calcDay(time = start, dayStartOffsetSeconds = offset))
    }

    // RepeatingDb.daytimeToTimeWithDayStart

    @Test
    fun daytimeToTime_nullDaytime_returnsNull() {
        assertNull(testRepeatingForDaytime(daytime = null).daytimeToTimeWithDayStart(100))
    }

    @Test
    fun daytimeToTime_zeroOffset_landsToday() {
        try {
            Cache.overrideListsForTesting(kvDb = emptyList())
            val today = UnixTime().localDay
            val expected = UnixTime.byLocalDay(today).localDayStartTime() + 3_600
            assertEquals(
                expected,
                testRepeatingForDaytime(daytime = 3_600).daytimeToTimeWithDayStart(today),
            )
        } finally {
            Cache.overrideListsForTesting(kvDb = emptyList())
        }
    }

    @Test
    fun daytimeToTime_positiveOffset_rollsToTomorrow() {
        try {
            seedDayStartOffset(7_200)
            val today = UnixTime().localDay
            val repeating = testRepeatingForDaytime(daytime = 3_600)
            // daytime < offset -> the daytime belongs to the next day-start day
            assertEquals(
                UnixTime.byLocalDay(today + 1).localDayStartTime() + 3_600,
                repeating.daytimeToTimeWithDayStart(today),
            )
        } finally {
            Cache.overrideListsForTesting(kvDb = emptyList())
        }
    }

    @Test
    fun daytimeToTime_positiveOffset_atOffset_staysToday() {
        try {
            seedDayStartOffset(7_200)
            val today = UnixTime().localDay
            assertEquals(
                UnixTime.byLocalDay(today).localDayStartTime() + 7_200,
                testRepeatingForDaytime(daytime = 7_200).daytimeToTimeWithDayStart(today),
            )
        } finally {
            Cache.overrideListsForTesting(kvDb = emptyList())
        }
    }

    @Test
    fun daytimeToTime_negativeOffset_eveningLandsYesterday() {
        try {
            seedDayStartOffset(-3_600)
            val today = UnixTime().localDay
            // daytime >= 86_400 - |offset| -> belongs to previous day-start day
            assertEquals(
                UnixTime.byLocalDay(today - 1).localDayStartTime() + 82_800,
                testRepeatingForDaytime(daytime = 82_800).daytimeToTimeWithDayStart(today),
            )
            // daytime below the threshold stays today
            assertEquals(
                UnixTime.byLocalDay(today).localDayStartTime() + 3_600,
                testRepeatingForDaytime(daytime = 3_600).daytimeToTimeWithDayStart(today),
            )
        } finally {
            Cache.overrideListsForTesting(kvDb = emptyList())
        }
    }
}

private fun seedDayStartOffset(seconds: Int) {
    Cache.overrideListsForTesting(kvDb = listOf(KvDb("DAY_START_OFFSET_SECONDS", seconds.toString())))
}

private fun testRepeatingForDaytime(
    daytime: Int?,
): RepeatingDb = RepeatingDb(
    id = 1, text = "jog", last_day = 0,
    type_id = 1, value = "1", daytime = daytime,
    is_important = 0, in_calendar = 0,
)
