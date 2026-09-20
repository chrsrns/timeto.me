package me.timeto.shared

import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskSortingTest {

    @Test
    fun sortedUi_today_ordersByTimeThenIdDesc() {
        try {
            seedTodayFolder()
            val tasks = listOf(
                testTaskUi(id = 1, text = "no time"),
                testTaskUi(id = 2, text = "late #e2000000002"),
                testTaskUi(id = 3, text = "early #e2000000001"),
            )
            // Untimed tasks (time = 0) first, then ascending time
            assertEquals(
                listOf(1, 3, 2),
                tasks.sortedUi(isToday = true).map { it.taskDb.id },
            )
        } finally {
            Cache.taskFoldersDbSorted = emptyList()
        }
    }

    @Test
    fun sortedUi_today_sameTime_idDescending() {
        try {
            seedTodayFolder()
            val tasks = listOf(
                testTaskUi(id = 3, text = "a #e2000000001"),
                testTaskUi(id = 4, text = "b #e2000000001"),
            )
            assertEquals(
                listOf(4, 3),
                tasks.sortedUi(isToday = true).map { it.taskDb.id },
            )
        } finally {
            Cache.taskFoldersDbSorted = emptyList()
        }
    }

    @Test
    fun sortedUi_notToday_idDescending() {
        try {
            seedTodayFolder()
            val tasks = listOf(
                testTaskUi(id = 1, text = "no time"),
                testTaskUi(id = 3, text = "early #e2000000001"),
                testTaskUi(id = 2, text = "late #e2000000002"),
            )
            assertEquals(
                listOf(3, 2, 1),
                tasks.sortedUi(isToday = false).map { it.taskDb.id },
            )
        } finally {
            Cache.taskFoldersDbSorted = emptyList()
        }
    }
}

// #e<10 digits> parses to a FromEvent time used for today ordering.
private fun seedTodayFolder() {
    Cache.taskFoldersDbSorted = listOf(
        TaskFolderDb(id = 1, sort = 1, activity_id = null, name = "Today", symbol_raw = ""),
    )
}

private fun testTaskUi(
    id: Int,
    text: String,
    folderId: Int = 1,
): TaskUi = TaskUi(TaskDb(id = id, folder_id = folderId, text = text))
