package me.timeto.shared

import kotlinx.datetime.LocalDate
import me.timeto.shared.db.RepeatingDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RepeatingPeriodTest {

    @Test
    fun everyNDays_belowOne_throws() {
        listOf(0, -1, -3).forEach { nDays ->
            val e = assertFailsWith<UiException>("nDays=$nDays") {
                RepeatingDb.Period.EveryNDays(nDays)
            }
            assertEquals("EveryNDays nDays < 1", e.uiMessage)
        }
    }

    @Test
    fun everyNDays_valid_titleAndValue() {
        assertEquals("Every day", RepeatingDb.Period.EveryNDays(1).title)
        assertEquals("Every 3 days", RepeatingDb.Period.EveryNDays(3).title)
        assertEquals("3", RepeatingDb.Period.EveryNDays(3).value)
        assertEquals(RepeatingDb.TYPE.EVERY_N_DAYS, RepeatingDb.Period.EveryNDays(3).type)
    }

    @Test
    fun daysOfWeek_empty_throws() {
        val e = assertFailsWith<UiException> {
            RepeatingDb.Period.DaysOfWeek(emptySet())
        }
        assertEquals("DaysOfWeek no days selected", e.uiMessage)
    }

    @Test
    fun daysOfWeek_outOfRange_throws() {
        listOf(setOf(0, 7), setOf(-1), setOf(99)).forEach { days ->
            val e = assertFailsWith<UiException>("days=$days") {
                RepeatingDb.Period.DaysOfWeek(days)
            }
            assertEquals("DaysOfWeek invalid data", e.uiMessage)
        }
    }

    @Test
    fun daysOfWeek_valid_titleAndValue() {
        assertEquals("Every day", RepeatingDb.Period.DaysOfWeek((0..6).toSet()).title)
        val period = RepeatingDb.Period.DaysOfWeek(setOf(0, 2))
        assertEquals("0,2", period.value)
        assertEquals("Mon Wed", period.title)
        assertEquals(RepeatingDb.TYPE.DAYS_OF_WEEK, period.type)
    }

    @Test
    fun daysOfMonth_empty_throws() {
        val e = assertFailsWith<UiException> {
            RepeatingDb.Period.DaysOfMonth(emptySet())
        }
        assertEquals("DaysOfMonth no days selected.", e.uiMessage)
    }

    @Test
    fun daysOfMonth_outOfRange_throws() {
        // Valid range is 0..MAX_DAY_OF_MONTH(27); 0 = LAST_DAY_OF_MONTH
        listOf(setOf(28), setOf(-1), setOf(1, 99)).forEach { days ->
            val e = assertFailsWith<UiException>("days=$days") {
                RepeatingDb.Period.DaysOfMonth(days)
            }
            assertEquals("DaysOfMonth invalid data.", e.uiMessage)
        }
    }

    @Test
    fun daysOfMonth_valid_boundariesAndTitle() {
        assertEquals("0", RepeatingDb.Period.DaysOfMonth(setOf(0)).value)
        assertEquals(
            "Last day of month",
            RepeatingDb.Period.DaysOfMonth(setOf(0)).title,
        )
        assertEquals(
            "1st of each month",
            RepeatingDb.Period.DaysOfMonth(setOf(1)).title,
        )
        assertEquals(
            "1st, 15th",
            RepeatingDb.Period.DaysOfMonth(setOf(1, 15)).title,
        )
        assertEquals(
            "1st, Last day",
            RepeatingDb.Period.DaysOfMonth(setOf(0, 1)).title,
        )
        assertEquals(RepeatingDb.TYPE.DAYS_OF_MONTH, RepeatingDb.Period.DaysOfMonth(setOf(1)).type)
    }
}

private fun testRepeatingDb(
    id: Int = 1,
    text: String = "jog",
    last_day: Int = 100,
    type_id: Int = 1,
    value: String = "1",
    daytime: Int? = null,
    is_important: Int = 0,
    in_calendar: Int = 0,
): RepeatingDb = RepeatingDb(
    id = id, text = text, last_day = last_day,
    type_id = type_id, value = value, daytime = daytime,
    is_important = is_important, in_calendar = in_calendar,
)

private fun epochDay(year: Int, month: Int, day: Int): Int =
    LocalDate(year, month, day).toEpochDays().toInt()
