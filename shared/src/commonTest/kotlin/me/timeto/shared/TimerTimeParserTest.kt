package me.timeto.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimerTimeParserTest {

    @Test
    fun parse_plainMinutes() {
        val parsed = TimerTimeParser.parse("10min")!!
        assertEquals(600, parsed.seconds)
        assertEquals("10min", parsed.match)
    }

    @Test
    fun parse_spaceBeforeMin() {
        val parsed = TimerTimeParser.parse("5 min")!!
        assertEquals(300, parsed.seconds)
        assertEquals("5 min", parsed.match)
    }

    @Test
    fun parse_caseInsensitive() {
        listOf("3 MIN", "3 Min", "3 mIn").forEach { text ->
            val parsed = TimerTimeParser.parse(text)!!
            assertEquals(180, parsed.seconds, "text=$text")
            assertEquals(text, parsed.match)
        }
    }

    @Test
    fun parse_embeddedInSentence() {
        val parsed = TimerTimeParser.parse("read for 20min now")!!
        assertEquals(1_200, parsed.seconds)
        assertEquals("20min", parsed.match)
    }

    @Test
    fun parse_firstMatchWins() {
        val parsed = TimerTimeParser.parse("10min or 20min")!!
        assertEquals(600, parsed.seconds)
        assertEquals("10min", parsed.match)
    }

    @Test
    fun parse_noMatch_returnsNull() {
        listOf("abc", "10", "min", "10 hours", "").forEach { text ->
            assertNull(TimerTimeParser.parse(text), "text=$text")
        }
    }

    @Test
    fun parse_substringMatch_plural() {
        // Regex is unanchored: "10mins" contains "10min"
        val parsed = TimerTimeParser.parse("10mins")!!
        assertEquals(600, parsed.seconds)
        assertEquals("10min", parsed.match)
    }
}
