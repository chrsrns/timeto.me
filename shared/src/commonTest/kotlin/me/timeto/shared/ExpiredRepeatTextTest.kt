package me.timeto.shared

import me.timeto.shared.db.IntervalDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpiredRepeatTextTest {

    @Test
    fun title_isVerbatim() {
        val alarms = buildAlarms(repeatSeconds = 60)
        assertTrue(alarms.isNotEmpty())
        alarms.forEach { alarm ->
            assertEquals("Time Is Over ⏰", alarm.title)
        }
    }

    @Test
    fun text_minuteValues() {
        // k*X = 60 -> "1 min", 600 -> "10 min"
        val alarms = buildAlarms(repeatSeconds = 60)
        assertEquals("Overdue by 1 min", alarms[0].text)
        assertEquals("Overdue by 10 min", alarms[9].text)
    }

    @Test
    fun text_hourAndMixedValues() {
        // X = 3600: k=1 -> 3600 -> "1h"; k=2 -> 7200 -> "2h"
        val alarms = buildAlarms(repeatSeconds = 3_600)
        assertEquals("Overdue by 1h", alarms[0].text)
        assertEquals("Overdue by 2h", alarms[1].text)

        // X = 1800: k=3 -> 5400 -> 1:30
        val mixed = buildAlarms(repeatSeconds = 1_800)
        assertEquals("Overdue by 1:30", mixed[2].text)
    }

    @Test
    fun text_matchesNominalKxNotWallClock() {
        // Timer still running: first nag fires at finishTime + X,
        // but the body always reports the nominal k*X overdue time.
        val alarms = buildExpiredRepeatNotifications(
            timerType = IntervalDb.TimerType.Timer(startTime = 1_000, timer = 600),
            repeatSeconds = 60,
            now = 1_200,
            liveActivity = testLiveActivity(),
        )
        assertEquals("Overdue by 1 min", alarms.first().text)
        assertEquals(400 + 60, alarms.first().inSeconds)
    }
}

private fun buildAlarms(repeatSeconds: Int): List<NotificationAlarm> =
    buildExpiredRepeatNotifications(
        timerType = IntervalDb.TimerType.OverdueTimer(startTime = 0, overdueSeconds = 0),
        repeatSeconds = repeatSeconds,
        now = 1_000,
        liveActivity = testLiveActivity(),
    )

private fun testLiveActivity(): LiveActivity =
    LiveActivity(IntervalDb(id = 1, time = 1_000, activityId = 1, note = "test"))
