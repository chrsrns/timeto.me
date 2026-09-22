package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db
import me.timeto.shared.vm.app.syncTomorrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SyncTomorrowTest {

    @Test
    fun tomorrowToToday_onDayBoundary() = runBlocking {
        initTestDb()
        seedTaskFolders()

        // Task ids are unix times; one second before local midnight = previous day.
        val yesterdayTaskId: Int = UnixTime().localDayStartTime() - 1
        val todayTaskId: Int = time()
        db.taskQueries.insert(id = yesterdayTaskId, folder_id = TaskFolderDb.ID_TOMORROW, text = "old")
        db.taskQueries.insert(id = todayTaskId, folder_id = TaskFolderDb.ID_TOMORROW, text = "new")
        refreshCache()

        syncTomorrow(DayStartOffsetUtils.getToday())
        awaitTaskFolder(yesterdayTaskId, TaskFolderDb.ID_TODAY)

        // Automatic sync keeps the task id (replaceIfTmrw = false).
        val movedTaskDb = TaskDb.selectAsc().first { it.id == yesterdayTaskId }
        assertEquals(TaskFolderDb.ID_TODAY, movedTaskDb.folder_id)
        assertEquals("old", movedTaskDb.text)

        // Today's task stays in Tomorrow.
        assertEquals(
            TaskFolderDb.ID_TOMORROW,
            TaskDb.selectAsc().first { it.id == todayTaskId }.folder_id,
        )

        // Idempotent: second run moves nothing else.
        syncTomorrow(DayStartOffsetUtils.getToday())
        delay(300)
        assertNotNull(TaskDb.selectAsc().firstOrNull { it.id == todayTaskId && it.folder_id == TaskFolderDb.ID_TOMORROW })
        assertEquals(TaskFolderDb.ID_TODAY, TaskDb.selectAsc().first { it.id == yesterdayTaskId }.folder_id)
    }

    private suspend fun awaitTaskFolder(taskId: Int, folderId: Int) {
        withTimeout(5_000) {
            while (TaskDb.selectAsc().firstOrNull { it.id == taskId }?.folder_id != folderId)
                delay(50)
        }
    }
}
