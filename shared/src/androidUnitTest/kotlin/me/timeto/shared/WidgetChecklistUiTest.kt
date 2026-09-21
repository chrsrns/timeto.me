package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.widget.WidgetChecklistUi
import me.timeto.shared.widget.WidgetUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetChecklistUiTest {

    private suspend fun seedBase(activityName: String) {
        seedTaskFolders()
        insertActivitySq(
            id = 1, name = activityName,
            typeId = ActivityDb.Type.general.id, homeButtonSort = "8:0:6",
        )
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
    fun checklist_fromActivityToken() = runBlocking {
        initTestDb()
        val checklistDb = ChecklistDb.insertWithValidation("CL", isResetOnDayStarts = false)
        seedBase(activityName = "Work #c${checklistDb.id}")
        ChecklistItemDb.insertWithValidation("item1", checklistDb, isChecked = false)
        ChecklistItemDb.insertWithValidation("item2", checklistDb, isChecked = true)

        val widgetChecklistUi = buildWidget().widgetChecklistUi!!
        assertEquals(checklistDb.id, widgetChecklistUi.checklistDb.id)
        assertEquals(
            listOf("item1", "item2"),
            widgetChecklistUi.itemsUi.map { it.text },
        )
        assertEquals(listOf(false, true), widgetChecklistUi.itemsUi.map { it.itemDb.isChecked })
    }

    @Test
    fun checklist_nullWithoutToken() = runBlocking {
        initTestDb()
        seedBase(activityName = "Work")
        ChecklistDb.insertWithValidation("CL", isResetOnDayStarts = false)
        refreshCache()
        assertNull(buildWidget().widgetChecklistUi)
    }

    @Test
    fun toggle_persistsCheckTime() = runBlocking {
        initTestDb()
        val checklistDb = ChecklistDb.insertWithValidation("CL", isResetOnDayStarts = false)
        seedBase(activityName = "Work #c${checklistDb.id}")
        ChecklistItemDb.insertWithValidation("item1", checklistDb, isChecked = false)

        suspend fun checkTime(): Int =
            ChecklistItemDb.selectSorted().first().check_time

        WidgetChecklistUi.ItemUi(ChecklistItemDb.selectSorted().first()).toggle()
        withTimeout(10_000) { while (checkTime() == 0) delay(25) }
        assertTrue(checkTime() > 0)

        WidgetChecklistUi.ItemUi(ChecklistItemDb.selectSorted().first()).toggle()
        withTimeout(10_000) { while (checkTime() != 0) delay(25) }
        assertEquals(0, checkTime())
    }
}
