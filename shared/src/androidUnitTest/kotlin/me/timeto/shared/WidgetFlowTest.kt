package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.NoteDb
import me.timeto.shared.db.NoteFolderDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db
import me.timeto.shared.widget.WidgetFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// V172: WidgetFlow reloads on every table the widget renders —
// activities, checklists (+items), intervals, notes (+folders),
// tasks (+folders). KvDb stays silent (not widget-visible).
class WidgetFlowTest {

    @Test
    fun emitsOnWidgetVisibleTables() = runBlocking {
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

        expectEmit {
            db.taskQueries.insert(id = time() + 1, folder_id = TaskFolderDb.ID_TODAY, text = "t")
        }

        expectEmit {
            ChecklistDb.insertWithValidation("CL", isResetOnDayStarts = false)
        }

        expectEmit {
            ChecklistItemDb.insertWithValidation(
                "item",
                ChecklistDb.selectAsc().first(),
                isChecked = false,
            )
        }

        expectEmit {
            insertIntervalSq(id = 1, time = time() - 60, activityId = 1)
        }

        expectEmit {
            insertActivitySq(id = 2, name = "Extra", homeButtonSort = "8:0:6")
        }

        expectEmit {
            NoteFolderDb.insertNoValidation(id = 1, sort = 0, onHome = false, symbol = symbol, name = "Notes")
        }

        expectEmit {
            NoteDb.insertWithValidation(text = "note", noteFolderDb = NoteFolderDb.selectAllSorted().first())
        }

        expectEmit {
            TaskFolderDb.insertNoValidation(10, 3, null, "Extra Folder", symbol)
        }

        // Not widget-visible: KvDb stays silent
        expectSilent {
            KvDb.KEY.IS_SENDING_REPORTS.upsertInt(1)
        }

        assertTrue(emissions.size >= 10, "emissions=${emissions.size}")
        collectJob.cancel()
    }
}
