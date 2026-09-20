package me.timeto.shared

import me.timeto.shared.vm.settings.dayStartSecondsToString
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsUtilsTest {

    @Test
    fun dayStartSecondsToString_wholeHours() {
        assertEquals("00:00", dayStartSecondsToString(0))
        assertEquals("01:00", dayStartSecondsToString(3_600))
        assertEquals("12:00", dayStartSecondsToString(43_200))
        assertEquals("23:00", dayStartSecondsToString(82_800))
    }

    @Test
    fun dayStartSecondsToString_negativeWraps() {
        assertEquals("23:00", dayStartSecondsToString(-3_600))
        assertEquals("22:00", dayStartSecondsToString(-7_200))
    }

    @Test
    fun dayStartSecondsToString_nonWholeHour_error() {
        // Invalid input also fires reportApi, which fails harmlessly
        // inside a background IO coroutine without a db.
        assertEquals("error", dayStartSecondsToString(100))
        assertEquals("error", dayStartSecondsToString(61))
    }
}
