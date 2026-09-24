package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db
import me.timeto.shared.vm.home.HomeMode
import me.timeto.shared.vm.home.tasks.HomeTasksItemUi
import me.timeto.shared.widget.WidgetUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WidgetUiBuildTest {

    private suspend fun seedWidgetBase(homeButtonSort: String = "8:0:6") {
        seedTaskFolders()
        insertActivitySq(id = 1, name = "Work", homeButtonSort = homeButtonSort)
        // HomeMode.TaskFolder falls back to the "Other" activity; keep it
        // off the button grid (rowIdx >= visibleRows) so heights stay testable.
        insertActivitySq(
            id = 9, name = "Other", typeId = ActivityDb.Type.other.id,
            homeButtonSort = "8:0:6",
        )
        insertIntervalSq(id = 1, time = time() - 60, activityId = 1)
        refreshCache()
    }

    private suspend fun buildWidget(): WidgetUi =
        WidgetUi.build(width = 400f, rowHeight = 50f, spacing = 8f)

    @Test
    fun height_zeroWhenNoButtons() = runBlocking {
        initTestDb()
        seedWidgetBase(homeButtonSort = "8:0:6") // rowIdx >= visibleRows → hidden
        assertEquals(0f, buildWidget().height)
    }

    @Test
    fun height_derivedFromMaxRowIdx() = runBlocking {
        initTestDb()
        seedWidgetBase(homeButtonSort = "0:0:6")
        // Rows are packed by homeButtonsUiSorted — three declared rows
        // produce three rendered rows.
        insertActivitySq(id = 2, name = "B", homeButtonSort = "1:0:6")
        insertActivitySq(id = 3, name = "C", homeButtonSort = "2:0:6")
        refreshCache()
        assertEquals(150f, buildWidget().height)
    }

    @Test
    fun build_readsLiveDb_taskAndChecklistItem() = runBlocking {
        initTestDb()
        seedWidgetBase()
        assertTrue(
            (buildWidget().homeBarUi.homeMode as HomeMode.TaskFolder)
                .homeTasksItemsUi.isEmpty()
        )

        // Live read: no refreshCache between mutation and rebuild.
        db.taskQueries.insert(id = time() + 1, folder_id = TaskFolderDb.ID_TODAY, text = "new task")
        val itemsUi = (buildWidget().homeBarUi.homeMode as HomeMode.TaskFolder)
            .homeTasksItemsUi
        assertEquals(
            listOf("new task"),
            itemsUi.filterIsInstance<HomeTasksItemUi.HomeTaskUi>().map { it.taskUi.tf.textNoFeatures },
        )

        // Checklist item read live as well.
        val checklistDb = ChecklistDb.insertWithValidation("CL", isResetOnDayStarts = false)
        Cache.requireActivity(1)
            .updateNameWithValidation("Work #c${checklistDb.id}")
        refreshCache()
        ChecklistItemDb.insertWithValidation("item1", checklistDb, isChecked = false)
        val items1 = buildWidget().widgetChecklistUi!!.itemsUi
        ChecklistItemDb.insertWithValidation("item2", checklistDb, isChecked = false)
        val items2 = buildWidget().widgetChecklistUi!!.itemsUi
        assertEquals(1, items1.size)
        assertEquals(2, items2.size)
    }

    @Test
    fun today_isFolderBased_notDayStartOffset() = runBlocking {
        initTestDb()
        seedWidgetBase()
        KvDb.KEY.DAY_START_OFFSET_SECONDS.upsertInt(3 * 3_600)
        refreshCache()
        // Task id is a unix time (creation moment); yesterday's id must
        // not be filtered out — "today" is decided by folder only.
        db.taskQueries.insert(
            id = time() - 86_400,
            folder_id = TaskFolderDb.ID_TODAY,
            text = "yesterday id",
        )
        db.taskQueries.insert(
            id = time() + 1,
            folder_id = TaskFolderDb.ID_SOMEDAY,
            text = "someday",
        )

        val itemsUi = (buildWidget().homeBarUi.homeMode as HomeMode.TaskFolder)
            .homeTasksItemsUi
        val texts = itemsUi.filterIsInstance<HomeTasksItemUi.HomeTaskUi>()
            .map { it.taskUi.tf.textNoFeatures }
        assertEquals(listOf("yesterday id"), texts)
    }

    @Test
    fun timerType_derivedFromStoredInterval() = runBlocking {
        initTestDb()
        seedWidgetBase()
        // selectDesc orders by time desc — this must be the latest interval.
        val intervalTime = time()
        insertIntervalSq(id = 2, time = intervalTime, activityId = 1, note = "#t600")
        refreshCache()

        // TimerType is a plain class (no equals) — assert fields.
        val timerType1 = buildWidget().timerStateUi.timerType
        assertIs<IntervalDb.TimerType.Timer>(timerType1)
        assertEquals(intervalTime, timerType1.startTime)
        assertEquals(600, timerType1.timer)
        // Re-init simulates process death: derivation is stored-interval
        // only, so the rebuilt state is identical.
        refreshCache()
        val timerType2 = buildWidget().timerStateUi.timerType
        assertIs<IntervalDb.TimerType.Timer>(timerType2)
        assertEquals(intervalTime, timerType2.startTime)
        assertEquals(600, timerType2.timer)
    }

    @Test
    fun isPurple_never() = runBlocking {
        initTestDb()
        seedWidgetBase()
        // Finished timer → red; a purple widget would render purple.
        // Must be the latest interval by `time` (selectDesc order).
        insertIntervalSq(id = 2, time = time() - 30, activityId = 1, note = "#t1")
        refreshCache()

        val timerStateUi = buildWidget().timerStateUi
        assertEquals(ColorEnum.red, timerStateUi.timerColor)
        assertEquals(ColorEnum.red, timerStateUi.controlsColorEnum)
        assertNotEquals(ColorEnum.purple, timerStateUi.timerColor)
        assertNotEquals(ColorEnum.purple, timerStateUi.controlsColorEnum)
    }

    @Test
    fun tasksList_todayFolderOnly_descThenReversed() = runBlocking {
        initTestDb()
        seedWidgetBase()
        val id1 = time() + 1
        val id2 = time() + 2
        db.taskQueries.insert(id = id1, folder_id = TaskFolderDb.ID_TODAY, text = "t1")
        db.taskQueries.insert(id = id2, folder_id = TaskFolderDb.ID_TODAY, text = "t2")
        db.taskQueries.insert(id = time() + 3, folder_id = TaskFolderDb.ID_SOMEDAY, text = "t3")

        val homeMode = buildWidget().homeBarUi.homeMode
        assertIs<HomeMode.TaskFolder>(homeMode)
        assertTrue(homeMode.taskFolderDb.isToday)

        val taskIds = homeMode.homeTasksItemsUi
            .filterIsInstance<HomeTasksItemUi.HomeTaskUi>()
            .map { it.taskUi.taskDb.id }
        assertEquals(listOf(id2, id1), taskIds)
        // Widget views render the list reversed (MyWidgetTasksView.kt:16).
        assertEquals(listOf(id1, id2), taskIds.reversed())
    }
}
