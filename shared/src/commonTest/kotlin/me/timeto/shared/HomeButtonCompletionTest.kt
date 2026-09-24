package me.timeto.shared

import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.vm.home.buttons.HomeButtonType
import me.timeto.shared.vm.task_form.TaskFormStrategy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeButtonCompletionTest {

    private val taskFolderDb = TaskFolderDb(
        id = TaskFolderDb.ID_TODAY, sort = 0, activity_id = null,
        name = "Today", symbol_raw = "icon--inbox",
    )

    @Test
    fun timerGoal_elapsedReachesGoal_completed() {
        val activityDb = testActivityDb(goalJson = ActivityDb.GoalType.Timer(seconds = 60).toJson())
        assertTrue(buildButton(activityDb, intervalsSeconds = 60).isCompleted)
        assertTrue(buildButton(activityDb, intervalsSeconds = 120).isCompleted)
    }

    @Test
    fun timerGoal_elapsedBelowGoal_notCompleted() {
        val activityDb = testActivityDb(goalJson = ActivityDb.GoalType.Timer(seconds = 60).toJson())
        assertFalse(buildButton(activityDb, intervalsSeconds = 59).isCompleted)
    }

    @Test
    fun counterGoal_barsReachCount_completed() {
        val activityDb = testActivityDb(goalJson = ActivityDb.GoalType.Counter(count = 2).toJson())
        assertTrue(buildButton(activityDb, barsCount = 2).isCompleted)
        assertTrue(buildButton(activityDb, barsCount = 3).isCompleted)
        assertFalse(buildButton(activityDb, barsCount = 1).isCompleted)
    }

    @Test
    fun checklistGoal_allItemsChecked_completed() {
        try {
            Cache.overrideListsForTesting(checklistsDb = listOf(ChecklistDb(id = 1, name = "L", reset_day = 0)))
            Cache.overrideListsForTesting(checklistItemsDb = listOf(
                ChecklistItemDb(id = 1, text = "a", list_id = 1, check_time = 1, sort = 0),
                ChecklistItemDb(id = 2, text = "b", list_id = 1, check_time = 2, sort = 1),
            ))
            val activityDb = testActivityDb(
                name = "A #c1",
                goalJson = ActivityDb.GoalType.Checklist.toJson(),
            )
            assertTrue(buildButton(activityDb).isCompleted)
        } finally {
            Cache.overrideListsForTesting(checklistsDb = emptyList())
            Cache.overrideListsForTesting(checklistItemsDb = emptyList())
        }
    }

    @Test
    fun checklistGoal_partiallyChecked_notCompleted() {
        try {
            Cache.overrideListsForTesting(checklistsDb = listOf(ChecklistDb(id = 1, name = "L", reset_day = 0)))
            Cache.overrideListsForTesting(checklistItemsDb = listOf(
                ChecklistItemDb(id = 1, text = "a", list_id = 1, check_time = 1, sort = 0),
                ChecklistItemDb(id = 2, text = "b", list_id = 1, check_time = 0, sort = 1),
            ))
            val activityDb = testActivityDb(
                name = "A #c1",
                goalJson = ActivityDb.GoalType.Checklist.toJson(),
            )
            assertFalse(buildButton(activityDb).isCompleted)
        } finally {
            Cache.overrideListsForTesting(checklistsDb = emptyList())
            Cache.overrideListsForTesting(checklistItemsDb = emptyList())
        }
    }

    @Test
    fun checklistGoal_noItems_completed() {
        try {
            Cache.overrideListsForTesting(checklistsDb = listOf(ChecklistDb(id = 1, name = "L", reset_day = 0)))
            Cache.overrideListsForTesting(checklistItemsDb = emptyList())
            val activityDb = testActivityDb(
                name = "A #c1",
                goalJson = ActivityDb.GoalType.Checklist.toJson(),
            )
            // totalCount == completedCount == 0 -> vacuously complete
            assertTrue(buildButton(activityDb).isCompleted)
        } finally {
            Cache.overrideListsForTesting(checklistsDb = emptyList())
            Cache.overrideListsForTesting(checklistItemsDb = emptyList())
        }
    }

    @Test
    fun noGoal_alwaysCompleted() {
        assertTrue(buildButton(testActivityDb(goalJson = null)).isCompleted)
    }

    ///

    private fun testActivityDb(
        name: String = "A",
        goalJson: String? = null,
    ) = ActivityDb(
        id = 1,
        parent_id = null,
        type_id = ActivityDb.Type.general.id,
        name = name,
        goal_json = goalJson,
        timer = ActivityDb.TimerType.TimerPicker.dbValue,
        period_json = ActivityDb.Period.Weekly().toJson().toString(),
        symbol_raw = "icon--inbox",
        home_button_sort = "0:0:6",
        color_rgba = "1,2,3,255",
        keep_screen_on = 0,
        pomodoro_timer = 0,
        checklist_hint = 0,
        timer_hints = "",
        alarm_mode = null,
    )

    private fun buildButton(
        activityDb: ActivityDb,
        intervalsSeconds: Int = 0,
        barsCount: Int = 0,
    ): HomeButtonType.Activity = HomeButtonType.Activity(
        isActive = false,
        activityDb = activityDb,
        activityTf = activityDb.name.textFeatures(),
        bgColor = ColorRgba(1, 2, 3),
        barsActivityStats = DayBarsUi.ActivityStats(
            activityDb = activityDb,
            intervalsSeconds = intervalsSeconds,
            activeTimeFrom = null,
            barsCount = barsCount,
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
