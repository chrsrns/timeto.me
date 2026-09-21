package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskMoveToTimerTest {

    private val symbol = Symbol.Icon.IconEnum.inbox.toIcon()

    private suspend fun insertTask(text: String): TaskDb {
        db.taskQueries.insert(
            id = time() + 1 + TaskDb.selectAsc().size,
            folder_id = TaskFolderDb.ID_TODAY,
            text = text,
        )
        return TaskDb.selectAsc().last()
    }

    private suspend fun awaitTasksCount(count: Int) {
        withTimeout(10_000) {
            while (TaskDb.selectAsc().size != count)
                delay(25)
        }
    }

    @Test
    fun moveToTimer_appendsToIntervalNote() = runBlocking {
        initTestDb()
        TaskFolderDb.insertNoValidation(TaskFolderDb.ID_TODAY, 0, null, "Today", symbol)
        insertActivitySq(id = 1, name = "Work")
        insertIntervalSq(id = 1, time = time() - 600, note = "bar")
        refreshCache()
        val taskDb = insertTask("foo")

        TaskUi(taskDb).moveToTimer()
        awaitTasksCount(0)

        assertEquals("bar\nfoo", IntervalDb.selectLastOneOrNull()!!.note)
    }

    @Test
    fun moveToTimer_blankIntervalNote_setsNote() = runBlocking {
        initTestDb()
        TaskFolderDb.insertNoValidation(TaskFolderDb.ID_TODAY, 0, null, "Today", symbol)
        insertActivitySq(id = 1, name = "Work")
        insertIntervalSq(id = 1, time = time() - 600, note = null)
        refreshCache()
        val taskDb = insertTask("foo")

        TaskUi(taskDb).moveToTimer()
        awaitTasksCount(0)

        assertEquals("foo", IntervalDb.selectLastOneOrNull()!!.note)
    }

    @Test
    fun moveToTimer_emptyTaskText_deletesOnly() = runBlocking {
        initTestDb()
        TaskFolderDb.insertNoValidation(TaskFolderDb.ID_TODAY, 0, null, "Today", symbol)
        insertActivitySq(id = 1, name = "Work")
        insertIntervalSq(id = 1, time = time() - 600, note = "bar")
        refreshCache()
        val taskDb = insertTask("")

        TaskUi(taskDb).moveToTimer()
        awaitTasksCount(0)

        assertEquals("bar", IntervalDb.selectLastOneOrNull()!!.note)
    }

    @Test
    fun moveToTimer_noInterval_nothingHappens() = runBlocking {
        initTestDb()
        TaskFolderDb.insertNoValidation(TaskFolderDb.ID_TODAY, 0, null, "Today", symbol)
        refreshCache()
        val taskDb = insertTask("foo")

        TaskUi(taskDb).moveToTimer()
        // `selectLastOneOrNull()!!` throws on the background scope and is
        // swallowed by launchEx; assert state is unchanged.
        delay(500)

        assertTrue(IntervalDb.selectAsc().isEmpty())
        assertEquals(listOf(taskDb.id), TaskDb.selectAsc().map { it.id })
    }
}
