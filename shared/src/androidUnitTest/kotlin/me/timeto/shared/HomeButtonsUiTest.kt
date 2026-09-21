package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db
import me.timeto.shared.vm.home.buttons.HomeButtonType
import me.timeto.shared.vm.home.buttons.HomeButtonUi
import me.timeto.shared.vm.home.buttons.HomeButtonsVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HomeButtonsUiTest {

    @Test
    fun visibleOnly_whenPeriodTodayAndRowInBounds() = runBlocking {
        initTestDb()
        seedTaskFolders()
        insertIntervalSq(id = 1, time = time() - 60, activityId = 1)

        val todayWeekday: Int = UnixTime().dayOfWeek()
        val notTodayJson: String = ActivityDb.Period.DaysOfWeek(
            days = (0..6).toSet() - todayWeekday,
        ).toJson().toString()

        insertActivitySq(id = 1, name = "Visible", homeButtonSort = "0:0:6")
        insertActivitySq(id = 2, name = "NotToday", periodJson = notTodayJson, homeButtonSort = "1:0:6")
        insertActivitySq(id = 3, name = "HiddenRow", homeButtonSort = "8:0:6")
        refreshCache()

        val buttonsUi = HomeButtonsVm.buildButtonsUi(
            width = 400f, rowHeight = 50f, spacing = 8f,
        )
        assertNotNull(buttonsUi.firstOrNull { it.activityId() == 1 })
        assertNull(buttonsUi.firstOrNull { it.activityId() == 2 })
        assertNull(buttonsUi.firstOrNull { it.activityId() == 3 })
    }

    @Test
    fun overlappingCells_relocatedToNewRow() = runBlocking {
        initTestDb()
        seedTaskFolders()
        insertIntervalSq(id = 1, time = time() - 60, activityId = 1)

        insertActivitySq(id = 1, name = "A", homeButtonSort = "0:0:3")
        insertActivitySq(id = 2, name = "B", homeButtonSort = "0:2:4")
        refreshCache()

        val buttonsUi = HomeButtonsVm.buildButtonsUi(
            width = 400f, rowHeight = 50f, spacing = 8f,
        )
        val a = buttonsUi.first { it.activityId() == 1 }
        val b = buttonsUi.first { it.activityId() == 2 }
        assertEquals(HomeButtonSort(rowIdx = 0, cellIdx = 0, size = 3), a.sort)
        assertEquals(HomeButtonSort(rowIdx = 1, cellIdx = 0, size = 6), b.sort)
    }

    ///

    private fun seedTaskFolders() {
        db.taskFolderQueries.insert(
            id = TaskFolderDb.ID_TODAY, sort = 0,
            activity_id = null, name = "Today", symbol_raw = "icon--inbox",
        )
        db.taskFolderQueries.insert(
            id = TaskFolderDb.ID_TOMORROW, sort = 1,
            activity_id = null, name = "Tomorrow", symbol_raw = "icon--inbox",
        )
    }

    private fun HomeButtonUi.activityId(): Int? =
        (type as? HomeButtonType.Activity)?.activityDb?.id
}
