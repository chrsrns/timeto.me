package me.timeto.shared

import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.KvDb.Companion.asTimerExpiredRepeatSeconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpiredRepeatBatchTest {

    @Test
    fun timerRunning_baseDelayFromFinishTime() {
        // finishTime = 1000 + 600 = 1600, now = 1200 -> baseDelay = 400
        val alarms = buildExpiredRepeatNotifications(
            timerType = IntervalDb.TimerType.Timer(startTime = 1_000, timer = 600),
            repeatSeconds = 60,
            now = 1_200,
            liveActivity = testLiveActivity(),
        )
        assertEquals(48, alarms.size)
        assertEquals((1..48).map { 400 + it * 60 }, alarms.map { it.inSeconds })
        assertEquals((1..48).toList(), alarms.map { (it.type as NotificationAlarm.Type.ExpiredRepeat).k })
    }

    @Test
    fun timerFinished_baseDelayZero() {
        // finishTime = 1600 <= now = 2000 -> baseDelay = 0
        val alarms = buildExpiredRepeatNotifications(
            timerType = IntervalDb.TimerType.Timer(startTime = 1_000, timer = 600),
            repeatSeconds = 60,
            now = 2_000,
            liveActivity = testLiveActivity(),
        )
        assertEquals((1..48).map { it * 60 }, alarms.map { it.inSeconds })
    }

    @Test
    fun overdueTimer_baseDelayZero() {
        val alarms = buildExpiredRepeatNotifications(
            timerType = IntervalDb.TimerType.OverdueTimer(startTime = 1_000, overdueSeconds = 120),
            repeatSeconds = 600,
            now = 5_000,
            liveActivity = testLiveActivity(),
        )
        assertEquals((1..48).map { it * 600 }, alarms.map { it.inSeconds })
    }

    @Test
    fun stopwatch_emitsNothing() {
        val alarms = buildExpiredRepeatNotifications(
            timerType = IntervalDb.TimerType.Stopwatch(startTime = 1_000, startSeconds = 0),
            repeatSeconds = 60,
            now = 5_000,
            liveActivity = testLiveActivity(),
        )
        assertTrue(alarms.isEmpty())
    }

    @Test
    fun xZeroOrNegative_emitsNothing() {
        listOf(0, -60).forEach { x ->
            val alarms = buildExpiredRepeatNotifications(
                timerType = IntervalDb.TimerType.Timer(startTime = 1_000, timer = 600),
                repeatSeconds = x,
                now = 2_000,
                liveActivity = testLiveActivity(),
            )
            assertTrue(alarms.isEmpty(), "x=$x should emit nothing")
        }
    }

    @Test
    fun kBound_minInterval_fillsBatch() {
        // X = 60: k*60 <= 86400 for all k <= 48 -> full 48
        val alarms = buildExpiredRepeatNotifications(
            timerType = IntervalDb.TimerType.OverdueTimer(startTime = 0, overdueSeconds = 0),
            repeatSeconds = 60,
            now = 1_000,
            liveActivity = testLiveActivity(),
        )
        assertEquals(48, alarms.size)
    }

    @Test
    fun kBound_largeInterval_truncatesAtBatchMax() {
        // X = 1800: 48*1800 = 86400 <= 86400 -> still full 48
        val alarms = buildExpiredRepeatNotifications(
            timerType = IntervalDb.TimerType.OverdueTimer(startTime = 0, overdueSeconds = 0),
            repeatSeconds = 1_800,
            now = 1_000,
            liveActivity = testLiveActivity(),
        )
        assertEquals(48, alarms.size)
        assertEquals(1_800 * 48, alarms.last().inSeconds)
    }

    @Test
    fun kBound_horizonCapsK() {
        // X = 3600: k*3600 <= 86400 -> k <= 24
        val alarms = buildExpiredRepeatNotifications(
            timerType = IntervalDb.TimerType.OverdueTimer(startTime = 0, overdueSeconds = 0),
            repeatSeconds = 3_600,
            now = 1_000,
            liveActivity = testLiveActivity(),
        )
        assertEquals(24, alarms.size)
        assertTrue(alarms.all { ((it.type as NotificationAlarm.Type.ExpiredRepeat).k * 3_600) <= 86_400 })
    }

    @Test
    fun kBound_intervalOverHorizon_emitsNothing() {
        // X = 86401: even k=1 exceeds horizon
        val alarms = buildExpiredRepeatNotifications(
            timerType = IntervalDb.TimerType.OverdueTimer(startTime = 0, overdueSeconds = 0),
            repeatSeconds = 86_401,
            now = 1_000,
            liveActivity = testLiveActivity(),
        )
        assertTrue(alarms.isEmpty())
    }

    @Test
    fun storedX_validatedOnRead() {
        assertEquals(600, KvDb("k", "600").asTimerExpiredRepeatSeconds())
        assertEquals(60, KvDb("k", "60").asTimerExpiredRepeatSeconds())
        assertEquals(0, KvDb("k", "0").asTimerExpiredRepeatSeconds())
        assertEquals(0, KvDb("k", "-60").asTimerExpiredRepeatSeconds())
        assertEquals(0, KvDb("k", "30").asTimerExpiredRepeatSeconds())
        assertEquals(0, KvDb("k", "90").asTimerExpiredRepeatSeconds())
        assertEquals(0, KvDb("k", "abc").asTimerExpiredRepeatSeconds())
        assertEquals(0, (null as KvDb?).asTimerExpiredRepeatSeconds())
    }
}

// LiveActivity computes dynamicIslandTitle from the interval note,
// so a plain-text note keeps it off Cache.activitiesDb.
private fun testLiveActivity(): LiveActivity =
    LiveActivity(IntervalDb(id = 1, time = 1_000, activityId = 1, note = "test"))
