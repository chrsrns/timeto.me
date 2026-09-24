package me.timeto.shared

import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.vm.summary.prepActivitiesUi
import kotlin.test.Test
import kotlin.test.assertEquals

class SummaryActivitiesUiTest {

    @Test
    fun ratio_includesGapsInDenominator() {
        try {
            Cache.overrideListsForTesting(activitiesDb = listOf(testActivity(1)))
            // 3600s interval, 7200s gap, 1800s interval
            val day = dayBars(
                bar(interval(1, 1), 0, 3_600),
                bar(null, 3_600, 7_200),
                bar(interval(2, 1), 10_800, 1_800),
            )
            val activitiesUi = prepActivitiesUi(listOf(day))
            assertEquals(1, activitiesUi.size)

            val ui = activitiesUi.first()
            // Denominator = last.timeFinish - first.timeStart = 12_600,
            // gap counted in total but not in the activity's own 5_400s
            assertEquals(5_400, ui.seconds)
            assertEquals(5_400f / 12_600f, ui.ratio)
            assertEquals("42%", ui.percentageString)
            assertEquals(5_400, ui.seconds / 1) // activeDaysCount = 1
            assertEquals("1h 30m / day", ui.perDayString)
            assertEquals("1h 30m", ui.totalTimeString)
        } finally {
            Cache.overrideListsForTesting(activitiesDb = emptyList())
        }
    }

    @Test
    fun secondsPerDay_dividesByActiveDays() {
        try {
            Cache.overrideListsForTesting(activitiesDb = listOf(testActivity(1)))
            val day1 = dayBars(bar(interval(1, 1), 0, 3_600))
            val day2 = dayBars(bar(interval(2, 1), 86_400, 3_600))
            val ui = prepActivitiesUi(listOf(day1, day2)).first()

            assertEquals(7_200, ui.seconds)
            // activeDaysCount = 2 -> per-day is halved
            assertEquals("1h / day", ui.perDayString)
            assertEquals("2h", ui.totalTimeString)
        } finally {
            Cache.overrideListsForTesting(activitiesDb = emptyList())
        }
    }

    @Test
    fun parentChild_rollupAndRootOnly() {
        try {
            val parent = testActivity(1, name = "P")
            val child = testActivity(2, parentId = 1, name = "C")
            val other = testActivity(3, name = "Q")
            Cache.overrideListsForTesting(activitiesDb = listOf(parent, child, other))

            val day = dayBars(
                bar(interval(1, 1), 0, 100),
                bar(interval(2, 2), 100, 50),
                bar(interval(3, 3), 150, 200),
            )
            val top = prepActivitiesUi(listOf(day))

            // Roots only, sorted by rolled-up seconds desc
            assertEquals(listOf(3, 1), top.map { it.activityDb.id })

            val parentUi = top.first { it.activityDb.id == 1 }
            // Parent total = own 100 + child 50
            assertEquals(150, parentUi.seconds)
            assertEquals(1, parentUi.children.size)
            assertEquals(2, parentUi.children[0].activityDb.id)
            assertEquals(50, parentUi.children[0].seconds)
        } finally {
            Cache.overrideListsForTesting(activitiesDb = emptyList())
        }
    }

    ///

    private fun testActivity(
        id: Int,
        parentId: Int? = null,
        name: String = "A$id",
    ) = ActivityDb(
        id = id,
        parent_id = parentId,
        type_id = ActivityDb.Type.general.id,
        name = name,
        goal_json = null,
        timer = ActivityDb.TimerType.TimerPicker.dbValue,
        period_json = ActivityDb.Period.Weekly().toJson().toString(),
        symbol_raw = "icon--inbox",
        home_button_sort = "0:0:6",
        color_rgba = "1,2,3,255",
        keep_screen_on = 0,
        pomodoro_timer = 0,
        checklist_hint = 0,
        timer_hints = "",
    )

    private fun interval(id: Int, activityId: Int) = IntervalDb(
        id = id, time = 0, activityId = activityId, note = null,
    )

    private fun bar(intervalDb: IntervalDb?, timeStart: Int, seconds: Int) =
        DayBarsUi.BarUi(intervalDb = intervalDb, timeStart = timeStart, seconds = seconds)

    private fun dayBars(bars: List<DayBarsUi.BarUi>) = DayBarsUi(
        unixDay = 0,
        barsUi = bars,
        dayStringFormat = DayBarsUi.DAY_STRING_FORMAT.ALL,
    )

    private fun dayBars(vararg bars: DayBarsUi.BarUi) = dayBars(bars.toList())
}
