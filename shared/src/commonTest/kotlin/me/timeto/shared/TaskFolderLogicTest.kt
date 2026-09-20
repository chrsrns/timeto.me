package me.timeto.shared

import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.vm.home.tasks.homeTasksFoldersSorted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskFolderLogicTest {

    @Test
    fun specialFolderIds_mapToFlags() {
        val today = testTaskFolderDb(id = 1)
        assertTrue(today.isToday)
        assertFalse(today.isTomorrow)
        assertFalse(today.isSomeday)

        val tomorrow = testTaskFolderDb(id = 4)
        assertFalse(tomorrow.isToday)
        assertTrue(tomorrow.isTomorrow)
        assertFalse(tomorrow.isSomeday)

        val someday = testTaskFolderDb(id = 5)
        assertFalse(someday.isToday)
        assertFalse(someday.isTomorrow)
        assertTrue(someday.isSomeday)

        val custom = testTaskFolderDb(id = 42)
        assertFalse(custom.isToday)
        assertFalse(custom.isTomorrow)
        assertFalse(custom.isSomeday)
    }
}

private fun testTaskFolderDb(
    id: Int,
    sort: Int = 0,
    activity_id: Int? = null,
    name: String = "folder",
    symbol_raw: String = "",
): TaskFolderDb = TaskFolderDb(
    id = id, sort = sort, activity_id = activity_id,
    name = name, symbol_raw = symbol_raw,
)
