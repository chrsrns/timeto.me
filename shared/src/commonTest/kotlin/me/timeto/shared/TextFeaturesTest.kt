package me.timeto.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextFeaturesTest {

    // #t<raw> timerType encode/decode

    @Test
    fun timerType_positiveRaw_decodesTimer() {
        val tf = "work #t600".textFeatures()
        assertIs<TextFeatures.TimerType.Timer>(tf.timerType)
        assertEquals(600, (tf.timerType as TextFeatures.TimerType.Timer).seconds)
        assertEquals("work", tf.textNoFeatures)
    }

    @Test
    fun timerType_overdueRaw_decodesOverdueTimer() {
        // raw = 100_000_000 + overdueSeconds
        val tf = "#t100000120".textFeatures()
        assertIs<TextFeatures.TimerType.OverdueTimer>(tf.timerType)
        assertEquals(120, (tf.timerType as TextFeatures.TimerType.OverdueTimer).overdueSeconds)
    }

    @Test
    fun timerType_zeroRaw_decodesStopwatchZero() {
        val tf = "#t0".textFeatures()
        assertEquals(
            TextFeatures.TimerType.Stopwatch(0),
            tf.timerType,
        )
    }

    @Test
    fun timerType_negativeRaw_decodesStopwatch() {
        // raw = 0 - startSeconds
        val tf = "#t-300".textFeatures()
        assertEquals(
            TextFeatures.TimerType.Stopwatch(300),
            tf.timerType,
        )
    }

    @Test
    fun timerType_encodeDecodeRoundtrip() {
        listOf(
            TextFeatures.TimerType.Timer(600),
            TextFeatures.TimerType.OverdueTimer(120),
            TextFeatures.TimerType.Stopwatch(0),
            TextFeatures.TimerType.Stopwatch(300),
        ).forEach { timerType ->
            val raw = "#t${timerType.rawValue}"
            assertEquals(timerType, raw.textFeatures().timerType, "raw=$raw")
        }
    }

    @Test
    fun timerType_noToken_nullTimerType() {
        assertNull("plain text".textFeatures().timerType)
    }
}
