package me.timeto.shared

import me.timeto.shared.db.ActivityDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityLogicTest {

    // TimerType.build

    @Test
    fun timerTypeBuild_specialObjects() {
        assertEquals(ActivityDb.TimerType.RestOfGoal, ActivityDb.TimerType.build(0))
        assertEquals(ActivityDb.TimerType.TimerPicker, ActivityDb.TimerType.build(-1))
        assertEquals(ActivityDb.TimerType.StopwatchZero, ActivityDb.TimerType.build(-2))
        assertEquals(ActivityDb.TimerType.StopwatchDaily, ActivityDb.TimerType.build(-3))
    }

    @Test
    fun timerTypeBuild_positiveDbValue_fixedTimer() {
        assertEquals(
            ActivityDb.TimerType.FixedTimer(3_600),
            ActivityDb.TimerType.build(3_600),
        )
    }

    @Test
    fun timerTypeBuild_daytimeRange() {
        // dbValueRange = -86_499..-100; daytime = -(dbValue + 100)
        val start = ActivityDb.TimerType.build(-100)
        assertIs<ActivityDb.TimerType.Daytime>(start)
        assertEquals(0, start.dayTimeUi.seconds)

        // DaytimeUi is minute-precision: 86_399s decodes to 23:59 -> 86_340s
        val end = ActivityDb.TimerType.build(-86_499)
        assertIs<ActivityDb.TimerType.Daytime>(end)
        assertEquals(86_340, end.dayTimeUi.seconds)
    }

    @Test
    fun timerTypeBuild_daytimeDbValueRoundtrip() {
        val daytime = ActivityDb.TimerType.Daytime(DaytimeUi(hour = 10, minute = 30))
        // dbValue = -(37_800 + 100) = -37_900
        assertEquals(-37_900, daytime.dbValue)
        val rebuilt = ActivityDb.TimerType.build(daytime.dbValue)
        assertIs<ActivityDb.TimerType.Daytime>(rebuilt)
        assertEquals(DaytimeUi(hour = 10, minute = 30), rebuilt.dayTimeUi)
    }

    @Test
    fun timerTypeBuild_unknownDbValue_throws() {
        listOf(-4, -99, -86_500, Int.MIN_VALUE).forEach { dbValue ->
            assertFailsWith<UiException>("dbValue=$dbValue") {
                ActivityDb.TimerType.build(dbValue)
            }
        }
    }

    // GoalType json

    @Test
    fun goalTypeJson_roundtrip() {
        listOf(
            ActivityDb.GoalType.Timer(3_600),
            ActivityDb.GoalType.Counter(5),
            ActivityDb.GoalType.Checklist,
        ).forEach { goalType ->
            assertEquals(
                goalType,
                ActivityDb.GoalType.fromJson(goalType.toJson()),
            )
        }
    }

    @Test
    fun goalTypeJson_unknownType_throws() {
        assertFailsWith<Exception> {
            ActivityDb.GoalType.fromJson("""{"type":"wtf"}""")
        }
    }

    @Test
    fun buildGoalTypeOrNull_nullJson_returnsNull() {
        assertNull(testActivityDb(goal_json = null).buildGoalTypeOrNull())
    }

    @Test
    fun buildGoalTypeOrNull_parsesStoredJson() {
        val activityDb = testActivityDb(
            goal_json = ActivityDb.GoalType.Timer(60).toJson(),
        )
        assertEquals(ActivityDb.GoalType.Timer(60), activityDb.buildGoalTypeOrNull())
    }

    // Period validation

    @Test
    fun periodDaysOfWeek_emptyDays_throws() {
        assertFailsWith<UiException> {
            ActivityDb.Period.DaysOfWeek.buildWithValidation(emptySet())
        }
    }

    @Test
    fun periodDaysOfWeek_outOfRange_throws() {
        listOf(setOf(0, 7), setOf(-1), setOf(99)).forEach { days ->
            assertFailsWith<UiException>("days=$days") {
                ActivityDb.Period.DaysOfWeek.buildWithValidation(days)
            }
        }
    }

    @Test
    fun periodDaysOfWeek_valid_returnsDays() {
        val period = ActivityDb.Period.DaysOfWeek.buildWithValidation(setOf(1, 3, 5))
        assertEquals(setOf(1, 3, 5), period.days)
    }

    @Test
    fun periodJson_roundtrip() {
        val daysOfWeek = ActivityDb.Period.DaysOfWeek(setOf(0, 2, 4))
        val rebuilt = ActivityDb.Period.fromJson(daysOfWeek.toJson())
        assertIs<ActivityDb.Period.DaysOfWeek>(rebuilt)
        assertEquals(setOf(0, 2, 4), rebuilt.days)

        assertIs<ActivityDb.Period.Weekly>(
            ActivityDb.Period.fromJson(ActivityDb.Period.Weekly().toJson()),
        )
    }

    @Test
    fun periodJson_unknownType_throws() {
        val json = kotlinx.serialization.json.Json.parseToJsonElement("""{"type":99}""")
            .let { it as kotlinx.serialization.json.JsonObject }
        assertFailsWith<Exception> {
            ActivityDb.Period.fromJson(json)
        }
    }

    // buildTimerHints

    @Test
    fun buildTimerHints_parsesCommaSeparated() {
        assertEquals(
            listOf(300, 600),
            testActivityDb(timer_hints = "300,600").buildTimerHints(),
        )
    }

    @Test
    fun buildTimerHints_dropsNonPositiveAndInvalid() {
        assertEquals(
            listOf(300),
            testActivityDb(timer_hints = "0,-5,abc,300").buildTimerHints(),
        )
    }

    @Test
    fun buildTimerHints_distinct() {
        assertEquals(
            listOf(300, 600),
            testActivityDb(timer_hints = "300,300,600").buildTimerHints(),
        )
    }

    @Test
    fun buildTimerHints_empty_returnsEmpty() {
        assertEquals(
            emptyList(),
            testActivityDb(timer_hints = "").buildTimerHints(),
        )
    }

    @Test
    fun buildTimerHintsOrDefault_fallsBackWhenEmpty() {
        assertEquals(
            listOf(45 * 60),
            testActivityDb(timer_hints = "").buildTimerHintsOrDefault(),
        )
        assertEquals(
            listOf(45),
            testActivityDb(timer_hints = "45").buildTimerHintsOrDefault(),
        )
    }

    // nextColorCached

    @Test
    fun nextColorCached_emptyCache_returnsFirstPaletteColor() {
        try {
            Cache.activitiesDb = emptyList()
            assertEquals("52,199,89,255", ActivityDb.nextColorCached().toRgbaString())
        } finally {
            Cache.activitiesDb = emptyList()
        }
    }

    @Test
    fun nextColorCached_firstColorUsed_returnsNextUnused() {
        try {
            Cache.activitiesDb = listOf(testActivityDb(color_rgba = "52,199,89,255"))
            assertEquals("0,122,255,255", ActivityDb.nextColorCached().toRgbaString())

            Cache.activitiesDb = listOf(
                testActivityDb(id = 1, color_rgba = "52,199,89,255"),
                testActivityDb(id = 2, color_rgba = "0,122,255,255"),
            )
            assertEquals("255,59,48,255", ActivityDb.nextColorCached().toRgbaString())
        } finally {
            Cache.activitiesDb = emptyList()
        }
    }

    @Test
    fun nextColorCached_allColorsUsed_returnsPaletteMember() {
        try {
            Cache.activitiesDb = paletteRgbaStrings.mapIndexed { idx, rgba ->
                testActivityDb(id = idx + 1, color_rgba = rgba)
            }
            val picked = ActivityDb.nextColorCached().toRgbaString()
            assertTrue(picked in paletteRgbaStrings, "picked=$picked")
        } finally {
            Cache.activitiesDb = emptyList()
        }
    }
}

// Palette order from ActivityDb.colors (private there, mirrored here).
private val paletteRgbaStrings = listOf(
    "52,199,89,255", "0,122,255,255", "255,59,48,255", "255,204,0,255",
    "175,82,222,255", "255,149,0,255", "48,176,199,255", "88,86,214,255",
    "96,125,139,255", "162,132,94,255", "142,142,147,255",
    "255,112,67,255", "198,255,0,255",
)

private fun testActivityDb(
    id: Int = 1,
    parent_id: Int? = null,
    type_id: Int = 0,
    name: String = "test",
    goal_json: String? = null,
    timer: Int = 0,
    period_json: String = """{"type":2}""",
    symbol_raw: String = "",
    home_button_sort: String = "",
    color_rgba: String = "52,199,89,255",
    keep_screen_on: Int = 0,
    pomodoro_timer: Int = 0,
    checklist_hint: Int = 0,
    timer_hints: String = "",
): ActivityDb = ActivityDb(
    id = id, parent_id = parent_id, type_id = type_id, name = name,
    goal_json = goal_json, timer = timer, period_json = period_json,
    symbol_raw = symbol_raw, home_button_sort = home_button_sort,
    color_rgba = color_rgba, keep_screen_on = keep_screen_on,
    pomodoro_timer = pomodoro_timer, checklist_hint = checklist_hint,
    timer_hints = timer_hints,
)
