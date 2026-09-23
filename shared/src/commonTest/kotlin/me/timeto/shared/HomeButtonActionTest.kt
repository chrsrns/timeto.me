package me.timeto.shared

import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.vm.home.buttons.HomeButtonType
import me.timeto.shared.vm.task_form.TaskFormStrategy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeButtonActionTest {

    private val taskFolderDb = TaskFolderDb(
        id = TaskFolderDb.ID_TODAY, sort = 0, activity_id = null,
        name = "Today", symbol_raw = "icon--inbox",
    )

    @Test
    fun timerPicker_needsPicker_false() {
        // Widget/android bar treats false as "open app" (timer picker
        // required); db writes from the true branches run on a
        // background scope, so only the return value is asserted.
        assertFalse(buildButton(timer = ActivityDb.TimerType.TimerPicker.dbValue).onBarPressedOrNeedTimerPicker())
    }

    @Test
    fun fixedTimer_starts_true() {
        assertTrue(buildButton(timer = 600).onBarPressedOrNeedTimerPicker())
    }

    @Test
    fun stopwatchZero_starts_true() {
        assertTrue(buildButton(timer = ActivityDb.TimerType.StopwatchZero.dbValue).onBarPressedOrNeedTimerPicker())
    }

    @Test
    fun restOfGoal_starts_true() {
        assertTrue(buildButton(timer = ActivityDb.TimerType.RestOfGoal.dbValue).onBarPressedOrNeedTimerPicker())
    }

    ///

    private fun testActivityDb(timer: Int) = ActivityDb(
        id = 1,
        parent_id = null,
        type_id = ActivityDb.Type.general.id,
        name = "A",
        goal_json = null,
        timer = timer,
        period_json = ActivityDb.Period.Weekly().toJson().toString(),
        symbol_raw = "icon--inbox",
        home_button_sort = "0:0:6",
        color_rgba = "1,2,3,255",
        keep_screen_on = 0,
        pomodoro_timer = 0,
        checklist_hint = 0,
        timer_hints = "",
    )

    private fun buildButton(timer: Int): HomeButtonType.Activity {
        val activityDb = testActivityDb(timer)
        return HomeButtonType.Activity(
            isActive = false,
            activityDb = activityDb,
            activityTf = activityDb.name.textFeatures(),
            bgColor = ColorRgba(1, 2, 3),
            barsActivityStats = DayBarsUi.ActivityStats(
                activityDb = activityDb,
                intervalsSeconds = 0,
                activeTimeFrom = null,
                barsCount = 0,
            ),
            sort = HomeButtonSort(rowIdx = 0, cellIdx = 0, size = 6),
            timerHintUi = emptyList(),
            childActivitiesUi = emptyList(),
            newTaskTodayFormStrategy = TaskFormStrategy.NewTask(
                activityDb = activityDb,
                taskFolderDb = taskFolderDb,
            ),
            newTaskTomorrowFormStrategy = TaskFormStrategy.NewTask(
                activityDb = activityDb,
                taskFolderDb = taskFolderDb,
            ),
        )
    }
}
