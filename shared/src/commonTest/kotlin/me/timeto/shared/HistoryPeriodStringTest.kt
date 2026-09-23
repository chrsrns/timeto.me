package me.timeto.shared

import me.timeto.shared.vm.history.makePeriodString
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryPeriodStringTest {

    @Test
    fun makePeriodString_seconds() {
        assertEquals("0 sec", makePeriodString(0))
        assertEquals("59 sec", makePeriodString(59))
    }

    @Test
    fun makePeriodString_minutes() {
        assertEquals("1 min", makePeriodString(60))
        assertEquals("59 min", makePeriodString(3_599))
    }

    @Test
    fun makePeriodString_hours() {
        assertEquals("1h", makePeriodString(3_600))
        assertEquals("2h", makePeriodString(7_200))
        assertEquals("1h 01m", makePeriodString(3_660))
        assertEquals("2h 02m", makePeriodString(7_320))
        assertEquals("1h 59m", makePeriodString(7_140))
    }
}
