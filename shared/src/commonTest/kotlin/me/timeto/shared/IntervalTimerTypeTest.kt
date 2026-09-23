package me.timeto.shared

import me.timeto.shared.db.IntervalDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IntervalTimerTypeTest {

    // buildTimerType — decode from note #t<raw>

    @Test
    fun buildTimerType_timerNote() {
        val type = testIntervalDb(note = "work #t600").buildTimerType()
        assertIs<IntervalDb.TimerType.Timer>(type)
        assertEquals(1_000, type.startTime)
        assertEquals(600, type.timer)
    }

    @Test
    fun buildTimerType_overdueNote() {
        // raw = 100_000_000 + overdueSeconds
        val type = testIntervalDb(note = "#t100000120").buildTimerType()
        assertIs<IntervalDb.TimerType.OverdueTimer>(type)
        assertEquals(120, type.overdueSeconds)
    }

    @Test
    fun buildTimerType_stopwatchNote() {
        // raw = 0 - startSeconds
        val type = testIntervalDb(note = "#t-300").buildTimerType()
        assertIs<IntervalDb.TimerType.Stopwatch>(type)
        assertEquals(300, type.startSeconds)
    }

    @Test
    fun buildTimerType_noNote_stopwatchZero() {
        val type = testIntervalDb(note = null).buildTimerType()
        assertIs<IntervalDb.TimerType.Stopwatch>(type)
        assertEquals(1_000, type.startTime)
        assertEquals(0, type.startSeconds)
    }

    @Test
    fun buildTimerType_noteWithoutTimer_stopwatchZero() {
        val type = testIntervalDb(note = "plain note").buildTimerType()
        assertIs<IntervalDb.TimerType.Stopwatch>(type)
        assertEquals(0, type.startSeconds)
    }

    // Timer math

    @Test
    fun timer_finishTimeAndRemaining() {
        val timer = IntervalDb.TimerType.Timer(startTime = 1_000, timer = 600)
        assertEquals(1_600, timer.finishTime)
        assertEquals(400, timer.calcRemainingSeconds(now = 1_200))
        assertFalse(timer.isFinished(now = 1_599))
        assertTrue(timer.isFinished(now = 1_600))
    }

    @Test
    fun timer_expiredString() {
        assertEquals(
            "1 minute has expired",
            IntervalDb.TimerType.Timer(startTime = 0, timer = 60).buildExpiredString(),
        )
        assertEquals(
            "10 minutes have expired",
            IntervalDb.TimerType.Timer(startTime = 0, timer = 600).buildExpiredString(),
        )
    }

    @Test
    fun overdueTimer_calcOverdueSeconds() {
        val overdue = IntervalDb.TimerType.OverdueTimer(startTime = 1_000, overdueSeconds = 120)
        assertEquals(4_120, overdue.calcOverdueSeconds(now = 5_000))
    }

    @Test
    fun stopwatch_calcElapsedSeconds() {
        val stopwatch = IntervalDb.TimerType.Stopwatch(startTime = 1_000, startSeconds = 50)
        assertEquals(4_050, stopwatch.calcElapsedSeconds(now = 5_000))
    }
}

private fun testIntervalDb(
    id: Int = 1,
    time: Int = 1_000,
    activityId: Int = 1,
    note: String?,
): IntervalDb = IntervalDb(id = id, time = time, activityId = activityId, note = note)
