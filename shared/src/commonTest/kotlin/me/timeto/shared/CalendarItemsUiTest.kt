package me.timeto.shared

import me.timeto.shared.db.RepeatingDb
import me.timeto.shared.vm.calendar.CalendarDayVm
import me.timeto.shared.vm.calendar.buildItemsUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarItemsUiTest {

    private fun buildRepeating(
        lastDay: Int,
        inCalendar: Boolean,
    ): RepeatingDb = RepeatingDb(
        id = 1, text = "x", last_day = lastDay,
        type_id = RepeatingDb.TYPE.EVERY_N_DAYS.id, value = "1",
        daytime = null, is_important = 0, in_calendar = inCalendar.toInt10(),
    )

    @Test
    fun buildItemsUi_inCalendarGate() {
        val day = UnixTime().localDay
        val hidden = buildRepeating(lastDay = day - 5, inCalendar = false)
        val shown = buildRepeating(lastDay = day - 5, inCalendar = true)

        assertTrue(
            buildItemsUi(day, emptyList(), listOf(hidden))
                .filterIsInstance<CalendarDayVm.ItemUi.RepeatingUi>()
                .isEmpty()
        )
        assertEquals(
            1,
            buildItemsUi(day, emptyList(), listOf(shown))
                .filterIsInstance<CalendarDayVm.ItemUi.RepeatingUi>()
                .size
        )
    }

    @Test
    fun monthGridVsDayView_divergence_documented() {
        // V114: last_day == today -> isInDay includes today (day view shows it),
        // but getNextDaysUntilDay starts at getNextDay() > last_day (month grid skips it).
        val today = UnixTime().localDay
        val repeatingDb = buildRepeating(lastDay = today, inCalendar = true)

        assertTrue(repeatingDb.isInDay(today))
        val gridDays = repeatingDb.getNextDaysUntilDay(today + 2 * 366)
        assertFalse(today in gridDays)
        assertTrue((today + 1) in gridDays)
    }
}
