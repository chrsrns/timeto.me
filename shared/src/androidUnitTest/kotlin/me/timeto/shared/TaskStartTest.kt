package me.timeto.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskStartTest {

    private val todayFolderDb = TaskFolderDb(
        id = TaskFolderDb.ID_TODAY,
        sort = 0,
        activity_id = null,
        name = "Today",
        symbol_raw = Symbol.Icon.IconEnum.inbox.toIcon().raw,
    )

    @Test
    fun start_withActivity_startsIntervalAndDeletesTask() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work")
        refreshCache()
        TaskDb.insertWithValidation("do thing {{goal_1}}", folder = todayFolderDb)
        val taskDb = TaskDb.selectAsc().first()

        val justStarted = CompletableDeferred<Unit>()
        var timerNeeded = false
        taskDb.startIntervalForUi(
            ifJustStarted = { justStarted.complete(Unit) },
            ifTimerNeeded = { timerNeeded = true },
        )

        withTimeout(10_000) { justStarted.await() }
        val intervalDb = IntervalDb.selectAsc().single()
        assertEquals(1, intervalDb.activityId)
        val tf = intervalDb.note!!.textFeatures()
        assertEquals("do thing", tf.textNoFeatures)
        assertEquals(1, tf.activityDb?.id)
        assertIs<TextFeatures.TimerType.Stopwatch>(tf.timerType)
        assertTrue(TaskDb.selectAsc().isEmpty())
        assertTrue(!timerNeeded)
    }

    @Test
    fun start_withTimerHint_intervalNoteKeepsTimer() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work")
        refreshCache()
        TaskDb.insertWithValidation("do 30min {{goal_1}}", folder = todayFolderDb)
        val taskDb = TaskDb.selectAsc().first()

        val justStarted = CompletableDeferred<Unit>()
        taskDb.startIntervalForUi(
            ifJustStarted = { justStarted.complete(Unit) },
            ifTimerNeeded = {},
        )

        withTimeout(10_000) { justStarted.await() }
        val intervalDb = IntervalDb.selectAsc().single()
        val tf = intervalDb.note!!.textFeatures()
        assertEquals("do", tf.textNoFeatures)
        assertEquals(TextFeatures.TimerType.Timer(1800), tf.timerType)
        assertTrue(TaskDb.selectAsc().isEmpty())
    }

    @Test
    fun start_noActivity_callsTimerNeeded() = runBlocking {
        initTestDb()
        refreshCache()
        TaskDb.insertWithValidation("plain task", folder = todayFolderDb)
        val taskDb = TaskDb.selectAsc().first()

        var timerNeeded = false
        taskDb.startIntervalForUi(
            ifJustStarted = {},
            ifTimerNeeded = { timerNeeded = true },
        )

        assertTrue(timerNeeded)
        assertTrue(IntervalDb.selectAsc().isEmpty())
        assertEquals(1, TaskDb.selectAsc().size)
    }
}
