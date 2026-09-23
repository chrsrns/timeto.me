package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.TaskFolderDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ActivityDbTest {

    @Test
    fun insert_blankName_throws() = runBlocking {
        initTestDb()
        listOf("   ", "#t600").forEach { name ->
            val ex = assertFailsWith<UiException>("name=$name") {
                insertActivity(name = name)
            }
            assertEquals("Goal name is empty", ex.message)
        }
    }

    @Test
    fun updateName_blank_throws() = runBlocking {
        initTestDb()
        val activityDb = insertActivity(name = "A")
        val ex = assertFailsWith<UiException> {
            activityDb.updateNameWithValidation("  ")
        }
        assertEquals("Goal name is empty", ex.message)
    }

    @Test
    fun insert_secondOther_throws() = runBlocking {
        initTestDb()
        insertActivity(name = "Other", type = ActivityDb.Type.other)
        val ex = assertFailsWith<UiException> {
            insertActivity(name = "Other 2", type = ActivityDb.Type.other)
        }
        assertEquals("Other already exists", ex.message)
    }

    @Test
    fun delete_other_throws() = runBlocking {
        initTestDb()
        val otherDb = insertActivity(name = "Other", type = ActivityDb.Type.other)
        val ex = assertFailsWith<UiException> { otherDb.deleteWithValidation() }
        assertEquals("It's impossible to delete \"Other\" activity", ex.message)
    }

    @Test
    fun delete_generalActivity_succeeds() = runBlocking {
        initTestDb()
        insertActivity(name = "Other", type = ActivityDb.Type.other)
        val activityDb = insertActivity(name = "A")
        activityDb.deleteWithValidation()
        assertEquals(1, ActivityDb.selectAll().size)
    }

    @Test
    fun delete_referencedByTaskFolder_throws() = runBlocking {
        initTestDb()
        insertActivity(name = "Other", type = ActivityDb.Type.other)
        val activityDb = insertActivity(name = "A")
        TaskFolderDb.insertNoValidation(
            id = 99,
            sort = 1,
            activityDb = activityDb,
            name = "Folder X",
            symbol = Symbol.Icon.IconEnum.inbox.toIcon(),
        )
        val ex = assertFailsWith<UiException> { activityDb.deleteWithValidation() }
        assertEquals("Please remove Folder X tasks folder first", ex.message)
    }

    @Test
    fun update_recursiveParent_throws() = runBlocking {
        initTestDb()
        val parentDb = insertActivity(name = "A")
        insertActivity(name = "B", parent = parentDb)
        val ex = assertFailsWith<UiException> {
            parentDb.updateWithValidation(
                name = "A",
                goalType = null,
                timerType = ActivityDb.TimerType.TimerPicker,
                period = ActivityDb.Period.Weekly(),
                symbol = Symbol.Icon.IconEnum.inbox.toIcon(),
                colorRgba = ColorRgba(1, 2, 3),
                keepScreenOn = false,
                pomodoroTimer = 0,
                timerHints = emptyList(),
                parentActivityDb = ActivityDb.selectAll().first { it.name == "B" },
            )
        }
        assertEquals("Recursive parent activity error", ex.message)
    }

    @Test
    fun update_selfParent_throws() = runBlocking {
        initTestDb()
        val activityDb = insertActivity(name = "A")
        val ex = assertFailsWith<UiException> {
            activityDb.updateWithValidation(
                name = "A",
                goalType = null,
                timerType = ActivityDb.TimerType.TimerPicker,
                period = ActivityDb.Period.Weekly(),
                symbol = Symbol.Icon.IconEnum.inbox.toIcon(),
                colorRgba = ColorRgba(1, 2, 3),
                keepScreenOn = false,
                pomodoroTimer = 0,
                timerHints = emptyList(),
                parentActivityDb = activityDb,
            )
        }
        assertEquals("Recursive parent activity error", ex.message)
    }

    ///

    private suspend fun insertActivity(
        name: String,
        type: ActivityDb.Type = ActivityDb.Type.general,
        parent: ActivityDb? = null,
    ): ActivityDb = ActivityDb.insertWithValidation(
        name = name,
        goalType = null,
        timerType = ActivityDb.TimerType.TimerPicker,
        period = ActivityDb.Period.Weekly(),
        symbol = Symbol.Icon.IconEnum.inbox.toIcon(),
        colorRgba = ColorRgba(1, 2, 3),
        keepScreenOn = false,
        pomodoroTimer = 0,
        timerHints = emptyList(),
        parentActivityDb = parent,
        type = type,
    )
}
