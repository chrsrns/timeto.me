package me.timeto.shared

import dbsq.ActivitySq
import dbsq.IntervalSq
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db
import me.timeto.shared.vm.home.buttons.homeButtonsCellsCount

suspend fun refreshCache(): Unit = Cache.init()

/**
 * `Cache.firstIntervalDb`/`lastIntervalDb` are `lateinit` — once set they stay
 * initialized for the whole test JVM, making `AppVm`'s `fillInitData` gate
 * order-dependent. Nulls the backing fields so a fresh-db state is testable.
 */
fun resetCacheLateInit() {
    listOf("firstIntervalDb", "lastIntervalDb").forEach { name ->
        Cache::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(Cache, null)
        }
    }
}

fun seedTaskFolders() {
    db.taskFolderQueries.insert(
        id = TaskFolderDb.ID_TODAY, sort = 0,
        activity_id = null, name = "Today", symbol_raw = "icon--inbox",
    )
    db.taskFolderQueries.insert(
        id = TaskFolderDb.ID_TOMORROW, sort = 1,
        activity_id = null, name = "Tomorrow", symbol_raw = "icon--inbox",
    )
    db.taskFolderQueries.insert(
        id = TaskFolderDb.ID_SOMEDAY, sort = 2,
        activity_id = null, name = "Someday", symbol_raw = "icon--inbox",
    )
}

fun insertActivitySq(
    id: Int,
    name: String = "Activity $id",
    typeId: Int = ActivityDb.Type.general.id,
    parentId: Int? = null,
    goalJson: String? = null,
    timer: Int = ActivityDb.TimerType.TimerPicker.dbValue,
    periodJson: String = ActivityDb.Period.Weekly().toJson().toString(),
    homeButtonSort: String = HomeButtonSort(0, 0, homeButtonsCellsCount).string,
): ActivityDb {
    db.activityQueries.insert(
        ActivitySq(
            id = id,
            parent_id = parentId,
            type_id = typeId,
            name = name,
            goal_json = goalJson,
            timer = timer,
            period_json = periodJson,
            symbol_raw = Symbol.Icon.IconEnum.inbox.toIcon().raw,
            home_button_sort = homeButtonSort,
            color_rgba = "1,2,3,255",
            keep_screen_on = 0,
            pomodoro_timer = 0,
            checklist_hint = 0,
            timer_hints = "",
        )
    )
    return ActivityDb(
        id = id,
        parent_id = parentId,
        type_id = typeId,
        name = name,
        goal_json = goalJson,
        timer = timer,
        period_json = periodJson,
        symbol_raw = Symbol.Icon.IconEnum.inbox.toIcon().raw,
        home_button_sort = homeButtonSort,
        color_rgba = "1,2,3,255",
        keep_screen_on = 0,
        pomodoro_timer = 0,
        checklist_hint = 0,
        timer_hints = "",
    )
}

fun insertIntervalSq(
    id: Int,
    time: Int,
    activityId: Int = 1,
    note: String? = null,
): IntervalDb {
    db.intervalQueries.insertWithId(
        IntervalSq(
            id = id,
            time = time,
            activity_id = activityId,
            note = note,
        )
    )
    return IntervalDb(
        id = id,
        time = time,
        activityId = activityId,
        note = note,
    )
}
