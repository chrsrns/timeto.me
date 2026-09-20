package me.timeto.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DaytimeUiTest {

    @Test
    fun calcTimer_futureDaytime_usesToday() {
        val now = UnixTime()
        val nowDaytime = now.time - now.localDayStartTime()
        if (nowDaytime + 120 >= 86_400)
            return // too close to midnight to place a future daytime
        val daytimeUi = DaytimeUi.byDaytime(nowDaytime + 120)
        // byDaytime drops the sub-minute part, so the gap is 120 minus it
        val expected = daytimeUi.seconds - nowDaytime
        val timer = daytimeUi.calcTimer()
        assertTrue(
            timer.seconds in (expected - 2)..expected,
            "seconds=${timer.seconds} expected=$expected",
        )
    }

    @Test
    fun calcTimer_pastDaytime_rollsToTomorrow() {
        val now = UnixTime()
        val nowDaytime = now.time - now.localDayStartTime()
        if (nowDaytime < 120)
            return // too early in the day to place a past daytime
        val daytimeUi = DaytimeUi.byDaytime(nowDaytime - 120)
        val expected = daytimeUi.seconds - nowDaytime + 86_400
        val timer = daytimeUi.calcTimer()
        assertTrue(
            timer.seconds in (expected - 2)..expected,
            "seconds=${timer.seconds} expected=$expected",
        )
    }

    // DaytimePickerSliderUi.calcSliderTickIdx

    @Test
    fun calcSliderTickIdx_zeroPosition_returnsZero() {
        assertEquals(
            0,
            DaytimePickerSliderUi.calcSliderTickIdx(
                ticksSize = 24,
                stepTicks = 1,
                slideXPosition = 0f,
                stepPx = 10f,
            ),
        )
    }

    @Test
    fun calcSliderTickIdx_rounding() {
        // 1.4 steps rounds down to step 1
        assertEquals(
            5,
            DaytimePickerSliderUi.calcSliderTickIdx(
                ticksSize = 60,
                stepTicks = 5,
                slideXPosition = 14f,
                stepPx = 10f,
            ),
        )
        // 1.5 steps rounds up to step 2
        assertEquals(
            10,
            DaytimePickerSliderUi.calcSliderTickIdx(
                ticksSize = 60,
                stepTicks = 5,
                slideXPosition = 15f,
                stepPx = 10f,
            ),
        )
    }

    @Test
    fun calcSliderTickIdx_clampsToLastTick() {
        assertEquals(
            55,
            DaytimePickerSliderUi.calcSliderTickIdx(
                ticksSize = 60,
                stepTicks = 5,
                slideXPosition = 10_000f,
                stepPx = 10f,
            ),
        )
        assertEquals(
            23,
            DaytimePickerSliderUi.calcSliderTickIdx(
                ticksSize = 24,
                stepTicks = 1,
                slideXPosition = 10_000f,
                stepPx = 10f,
            ),
        )
    }

    @Test
    fun calcSliderTickIdx_negativePosition_clampsToZero() {
        assertEquals(
            0,
            DaytimePickerSliderUi.calcSliderTickIdx(
                ticksSize = 24,
                stepTicks = 1,
                slideXPosition = -50f,
                stepPx = 10f,
            ),
        )
    }
}
