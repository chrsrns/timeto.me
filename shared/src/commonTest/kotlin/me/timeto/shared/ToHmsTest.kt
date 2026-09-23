package me.timeto.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ToHmsTest {

    @Test
    fun toHms_basic() {
        assertEquals(listOf(0, 0, 0), 0.toHms())
        assertEquals(listOf(0, 0, 59), 59.toHms())
        assertEquals(listOf(0, 1, 0), 60.toHms())
        assertEquals(listOf(0, 59, 59), 3_599.toHms())
        assertEquals(listOf(1, 0, 0), 3_600.toHms())
        assertEquals(listOf(1, 1, 1), 3_661.toHms())
        assertEquals(listOf(23, 59, 59), 86_399.toHms())
    }

    @Test
    fun toHms_overDay_keepsCounting() {
        assertEquals(listOf(25, 0, 0), 90_000.toHms())
    }

    @Test
    fun toHms_roundToNextMinute() {
        // Exact minute stays
        assertEquals(listOf(0, 1, 0), 60.toHms(roundToNextMinute = true))
        // Any leftover second rounds up to the next minute
        assertEquals(listOf(0, 2, 0), 61.toHms(roundToNextMinute = true))
        assertEquals(listOf(0, 2, 0), 119.toHms(roundToNextMinute = true))
        assertEquals(listOf(1, 1, 0), 3_601.toHms(roundToNextMinute = true))
        // Without the flag the same inputs keep seconds
        assertEquals(listOf(0, 1, 1), 61.toHms())
        assertEquals(listOf(1, 0, 1), 3_601.toHms())
    }
}
