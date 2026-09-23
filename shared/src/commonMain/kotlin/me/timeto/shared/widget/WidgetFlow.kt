package me.timeto.shared.widget

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.NoteDb
import me.timeto.shared.db.NoteFolderDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.ioScope
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object WidgetFlow {

    val flow = MutableStateFlow<String?>(null)

    private var job: Job? = null

    @OptIn(ExperimentalUuidApi::class)
    fun startSafe() {
        job?.cancel()
        job = ioScope().launch {
            try {
                combine(
                    ActivityDb.anyChangeFlow(),
                    ChecklistDb.anyChangeFlow(),
                    ChecklistItemDb.anyChangeFlow(),
                    IntervalDb.anyChangeFlow(),
                    NoteDb.anyChangeFlow(),
                    NoteFolderDb.anyChangeFlow(),
                    TaskDb.anyChangeFlow(),
                    TaskFolderDb.anyChangeFlow(),
                ) {
                    flow.emit(Uuid.random().toHexString())
                }.collect()
            } catch (_: Exception) {
            }
        }
    }
}
