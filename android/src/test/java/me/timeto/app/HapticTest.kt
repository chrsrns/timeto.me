package me.timeto.app

import me.timeto.shared.timeMls
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// V225: skip vibrate when elapsed < duration * 1.5
class HapticTest {

    @Test
    fun noPreviousShot_notThrottled() {
        Haptic.oneShotLastMillis = 0
        assertFalse(Haptic.isThrottled(40))
        assertFalse(Haptic.isThrottled(70))
    }

    @Test
    fun justShot_throttled() {
        Haptic.oneShotLastMillis = timeMls()
        assertTrue(Haptic.isThrottled(40))
        assertTrue(Haptic.isThrottled(70))
    }

    @Test
    fun betweenWindows_onlyLongThrottled() {
        // shot window 40*1.5=60ms, long window 70*1.5=105ms
        Haptic.oneShotLastMillis = timeMls() - 80
        assertFalse(Haptic.isThrottled(40))
        assertTrue(Haptic.isThrottled(70))
    }

    @Test
    fun pastBothWindows_notThrottled() {
        Haptic.oneShotLastMillis = timeMls() - 200
        assertFalse(Haptic.isThrottled(40))
        assertFalse(Haptic.isThrottled(70))
    }
}
