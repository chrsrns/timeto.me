package me.timeto.shared

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
