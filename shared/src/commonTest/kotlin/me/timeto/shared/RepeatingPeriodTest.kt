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
