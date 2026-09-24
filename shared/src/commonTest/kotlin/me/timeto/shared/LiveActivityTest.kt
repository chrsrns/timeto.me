package me.timeto.shared

import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.IntervalDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LiveActivityTest {

    @Test
    fun timerType_derivedFromInterval() {
        val live = LiveActivity(
            testIntervalDb(time = 1_000, note = "work #t600"),
        )
        val timerType = live.timerType
        assertIs<IntervalDb.TimerType.Timer>(timerType)
        assertEquals(1_000, timerType.startTime)
        assertEquals(600, timerType.timer)
    }

    @Test
    fun timerType_noNote_isStopwatch() {
        try {
            Cache.overrideListsForTesting(activitiesDb = listOf(testActivityDb(id = 1, name = "Running")))
            val live = LiveActivity(testIntervalDb(note = null))
            val timerType = live.timerType
            assertIs<IntervalDb.TimerType.Stopwatch>(timerType)
        } finally {
            Cache.overrideListsForTesting(activitiesDb = emptyList())
        }
    }

    @Test
    fun dynamicIslandTitle_prefersNote() {
        try {
            Cache.overrideListsForTesting(activitiesDb = listOf(testActivityDb(id = 7, name = "Running")))
            val live = LiveActivity(
                testIntervalDb(activityId = 7, note = "morning jog"),
            )
            assertEquals("morning jog", live.dynamicIslandTitle)
        } finally {
            Cache.overrideListsForTesting(activitiesDb = emptyList())
        }
    }

    @Test
    fun dynamicIslandTitle_fallsBackToActivityName() {
        try {
            Cache.overrideListsForTesting(activitiesDb = listOf(testActivityDb(id = 7, name = "Running")))
            val live = LiveActivity(
                testIntervalDb(activityId = 7, note = null),
            )
            assertEquals("Running", live.dynamicIslandTitle)
        } finally {
            Cache.overrideListsForTesting(activitiesDb = emptyList())
        }
    }

    @Test
    fun dynamicIslandTitle_blankNote_fallsBack() {
        try {
            Cache.overrideListsForTesting(activitiesDb = listOf(testActivityDb(id = 7, name = "Running")))
            val live = LiveActivity(
                testIntervalDb(activityId = 7, note = "   "),
            )
            assertEquals("Running", live.dynamicIslandTitle)
        } finally {
            Cache.overrideListsForTesting(activitiesDb = emptyList())
        }
    }
}

private fun testIntervalDb(
    id: Int = 1,
    time: Int = 1_000,
    activityId: Int = 1,
    note: String? = null,
): IntervalDb = IntervalDb(
    id = id, time = time, activityId = activityId, note = note,
)

private fun testActivityDb(
    id: Int,
    name: String,
): ActivityDb = ActivityDb(
    id = id, parent_id = null, type_id = 0, name = name,
    goal_json = null, timer = 0, period_json = """{"type":2}""",
    symbol_raw = "", home_button_sort = "",
    color_rgba = "52,199,89,255", keep_screen_on = 0,
    pomodoro_timer = 0, checklist_hint = 0, timer_hints = "",
)
