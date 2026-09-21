package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.db.NoteDb
import me.timeto.shared.db.NoteFolderDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db
import me.timeto.shared.widget.WidgetFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetFlowTest {

    @Test
    fun emitsOnWatchedTables_only() = runBlocking {
        initTestDb()
        seedTaskFolders()
        insertActivitySq(id = 1, name = "Work", homeButtonSort = "8:0:6")
        insertActivitySq(id = 9, name = "Other", typeId = ActivityDb.Type.other.id, homeButtonSort = "8:0:6")
        val symbol = Symbol.Icon.IconEnum.inbox.toIcon()

        val emissions = mutableListOf<String?>()
        val collectJob = launch { WidgetFlow.flow.collect { emissions.add(it) } }
        yield()
        WidgetFlow.startSafe()

        // StateFlow replay + first combined emission (each watched
        // anyChangeFlow emits its initial query result first).
        withTimeout(10_000) { while (emissions.size < 2) delay(25) }

        suspend fun expectEmit(block: suspend () -> Unit) {
            val base = emissions.size
            block()
            withTimeout(10_000) { while (emissions.size == base) delay(25) }
        }

        suspend fun expectSilent(block: suspend () -> Unit) {
            val base = emissions.size
            block()
            delay(500)
            assertEquals(base, emissions.size)
        }

        // Watched: TaskDb
        expectEmit {
            db.taskQueries.insert(id = time() + 1, folder_id = TaskFolderDb.ID_TODAY, text = "t")
        }

        // Watched: ChecklistItemDb
        expectEmit {
            val checklistDb = ChecklistDb.insertWithValidation("CL", isResetOnDayStarts = false)
            ChecklistItemDb.insertWithValidation("item", checklistDb, isChecked = false)
        }

        // Watched: IntervalDb
        expectEmit {
            insertIntervalSq(id = 1, time = time() - 60, activityId = 1)
        }

        // Not watched: ActivityDb
        expectSilent {
            insertActivitySq(id = 2, name = "Extra", homeButtonSort = "8:0:6")
        }

        // Not watched: NoteDb / NoteFolderDb
        expectSilent {
            NoteFolderDb.insertNoValidation(id = 1, sort = 0, onHome = false, symbol = symbol, name = "Notes")
            NoteDb.insertWithValidation(text = "note", noteFolderDb = NoteFolderDb.selectAllSorted().first())
        }

        // Not watched: TaskFolderDb
        expectSilent {
            TaskFolderDb.insertNoValidation(10, 3, null, "Extra Folder", symbol)
        }

        assertTrue(emissions.size >= 4, "emissions=${emissions.size}")
        collectJob.cancel()
    }
}
