package me.timeto.shared

import me.timeto.shared.vm.history.form.HistoryFormUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryFormUtilsTest {

    @Test
    fun makeTimeNote_todayWithToday_prefixed() {
        val time = UnixTime().localDayStartTime() + 3_600
        assertEquals(
            "Today 01:00",
            HistoryFormUtils.makeTimeNote(time, withToday = true),
        )
    }

    @Test
    fun makeTimeNote_todayWithoutToday_hhmmOnly() {
        val time = UnixTime().localDayStartTime() + 3_600
        assertEquals(
            "01:00",
            HistoryFormUtils.makeTimeNote(time, withToday = false),
        )
    }

    @Test
    fun makeTimeNote_otherDay_includesDate() {
        val time = UnixTime().localDayStartTime() - 86_400 + 3_600
        val note = HistoryFormUtils.makeTimeNote(time, withToday = false)
        // "<day> <mon3>, <dow3> 01:00"
        assertTrue(
            Regex("^\\d{1,2} [A-Z][a-z]{2}, [A-Z][a-z]{2} 01:00$").matches(note),
            "note=$note",
        )
    }

    @Test
    fun makeTimeNote_otherDay_ignoresWithToday() {
        val time = UnixTime().localDayStartTime() - 86_400 + 3_600
        assertEquals(
            HistoryFormUtils.makeTimeNote(time, withToday = false),
            HistoryFormUtils.makeTimeNote(time, withToday = true),
        )
    }
}
