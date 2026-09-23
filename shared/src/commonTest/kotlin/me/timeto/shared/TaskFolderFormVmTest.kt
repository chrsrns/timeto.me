package me.timeto.shared

import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.vm.task_folder.TaskFolderFormVm
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskFolderFormVmTest {

    private fun buildFolder(id: Int): TaskFolderDb =
        TaskFolderDb(id = id, sort = 0, activity_id = null, name = "f$id", symbol_raw = "icon--inbox")

    private fun buildState(folderDb: TaskFolderDb?): TaskFolderFormVm.State =
        TaskFolderFormVm.State(folderDb = folderDb, activityDb = null, name = "x", symbol = null)

    @Test
    fun isActivityAvailable_newFolder_true() {
        assertTrue(buildState(folderDb = null).isActivityAvailable)
    }

    @Test
    fun isActivityAvailable_specialFolders_false() {
        assertFalse(buildState(buildFolder(TaskFolderDb.ID_TODAY)).isActivityAvailable)
        assertFalse(buildState(buildFolder(TaskFolderDb.ID_TOMORROW)).isActivityAvailable)
        assertFalse(buildState(buildFolder(TaskFolderDb.ID_SOMEDAY)).isActivityAvailable)
    }

    @Test
    fun isActivityAvailable_customFolder_true() {
        assertTrue(buildState(buildFolder(id = 2)).isActivityAvailable)
    }
}
