package me.timeto.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.timeto.shared.backups.Backupable__Holder
import me.timeto.shared.backups.Backupable__Item
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.ShortcutDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db

object SmartRestore {

    private var lastSyncId: Long? = null

    internal fun resetLastSyncIdForTesting() {
        lastSyncId = null
    }

    fun restore(
        jsonString: String,
    ): Unit {
        // Transaction to avoid many UI updates
        db.transaction {
            val json = Json.parseToJsonElement(jsonString)

            val newSyncId = json.jsonObject["type"]!!.jsonPrimitive.long
            val lastSyncIdLocal = lastSyncId
            if (lastSyncIdLocal != null && lastSyncIdLocal > newSyncId)
                return@transaction
            lastSyncId = newSyncId

            // Ordering is important
            // WARNING The same models must be in listenForSyncWatch()
            val checklistItems = smartRestore__start(ChecklistItemDb, json.jsonObject["checklist_items"]!!.jsonArray)
            val checklists = smartRestore__start(ChecklistDb, json.jsonObject["checklists"]!!.jsonArray)
            val shortcuts = smartRestore__start(ShortcutDb, json.jsonObject["shortcuts"]!!.jsonArray)
            val intervals = smartRestore__start(
                IntervalDb,
                json.jsonObject["intervals"]!!.jsonArray,
                doNotUpdate = true,
            )
            val tasks = smartRestore__start(TaskDb, json.jsonObject["tasks"]!!.jsonArray)
            val taskFolders = smartRestore__start(TaskFolderDb, json.jsonObject["task_folders"]!!.jsonArray)
            val activities = smartRestore__start(ActivityDb, json.jsonObject["activities"]!!.jsonArray)

            activities()
            taskFolders()
            tasks()
            intervals()
            shortcuts()
            checklists()
            checklistItems()

            // To 100% ensure
            val ifl = IntervalDb.selectFirstAndLastNeedTransaction()
            Cache.fillLateInit(firstInterval = ifl.first, lastInterval = ifl.second)
        }
    }
}

private fun smartRestore__start(
    backupableHolder: Backupable__Holder,
    jArray: JsonArray,
    doNotUpdate: Boolean = false,
): (() -> Unit) {
    val newIds = jArray.map { it.jsonArray[0].jsonPrimitive.content }.toSet()
    backupableHolder.backupable__getAll().forEach { item ->
        if (!newIds.contains(item.backupable__getId()))
            item.backupable__delete()
    }
    return {
        val oldItemsMap: Map<String, Backupable__Item> = backupableHolder
            .backupable__getAll()
            .associateBy { it.backupable__getId() }

        jArray
            .map { it.jsonArray }
            .forEach { j ->
                val newId = j[0].jsonPrimitive.content
                val oldItem = oldItemsMap[newId]
                if (oldItem != null) {
                    if (!doNotUpdate && (oldItem.backupable__backup().toString() != j.toString()))
                        oldItem.backupable__update(j)
                    return@forEach
                }
                backupableHolder.backupable__restore(j)
            }
    }
}
