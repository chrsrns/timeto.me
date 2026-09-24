package me.timeto.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import me.timeto.shared.db.*

object Cache {

    var checklistsDb = listOf<ChecklistDb>()
        private set
    var checklistItemsDb = listOf<ChecklistItemDb>()
        private set
    var shortcutsDb = listOf<ShortcutDb>()
        private set
    var notesDb = listOf<NoteDb>()
        private set
    var noteFoldersDb = listOf<NoteFolderDb>()
        private set
    var kvDb = listOf<KvDb>()
        private set
    var tasksDb = listOf<TaskDb>()
        private set
    var taskFoldersDbSorted = listOf<TaskFolderDb>()
        private set
    var eventsDb = listOf<EventDb>()
        private set
    var eventTemplatesDbSorted = listOf<EventTemplateDb>()
        private set
    var repeatingsDb = listOf<RepeatingDb>()
        private set
    var activitiesDb = listOf<ActivityDb>()
        private set

    lateinit var firstIntervalDb: IntervalDb
        private set
    lateinit var lastIntervalDb: IntervalDb
        private set

    //
    // Late Init

    fun isLateInitInitialized(): Boolean =
        ::firstIntervalDb.isInitialized && ::lastIntervalDb.isInitialized

    fun fillLateInit(firstInterval: IntervalDb, lastInterval: IntervalDb) {
        this.firstIntervalDb = firstInterval
        this.lastIntervalDb = lastInterval
        bumpVersion()
    }

    ///

    lateinit var todayTaskFolderDb: TaskFolderDb
        private set
    lateinit var tomorrowTaskFolderDb: TaskFolderDb
        private set
    lateinit var somedayTaskFolderDb: TaskFolderDb
        private set

    //
    // Readiness

    private var readyDeferred: Deferred<Unit>? = null

    suspend fun awaitReady() {
        val deferred: Deferred<Unit> =
            readyDeferred ?: ioScope().async { init() }.also { readyDeferred = it }
        deferred.await()
    }

    //
    // Change signal

    private val versionState = MutableStateFlow(0)

    val version: Flow<Int> = versionState.asStateFlow()

    private fun bumpVersion() {
        versionState.value = versionState.value + 1
    }

    //
    // By-id lookups
    //
    // Each index is rebuilt only when its source list is replaced, so a lookup
    // is O(1) and callers never scan or pick a miss policy themselves.
    // `requireX` throws NoSuchElementException on miss; `xOrNull` returns null.

    private var activityIndexCache: Pair<List<ActivityDb>, Map<Int, ActivityDb>>? = null

    private fun activityIndex(): Map<Int, ActivityDb> {
        val list = activitiesDb
        val cached = activityIndexCache
        if (cached != null && cached.first === list)
            return cached.second
        val index = list.associateBy { it.id }
        activityIndexCache = list to index
        return index
    }

    fun activityOrNull(id: Int): ActivityDb? =
        activityIndex()[id]

    fun requireActivity(id: Int): ActivityDb =
        activityIndex()[id] ?: throw NoSuchElementException("Activity $id not found")

    fun requireActivityByType(type: ActivityDb.Type): ActivityDb =
        activitiesDb.first { it.type_id == type.id }

    fun activityDescendantsMap(): Map<Int, List<ActivityDb>> {
        val all = activitiesDb
        val resMap: Map<Int, MutableList<ActivityDb>> =
            all.associate { it.id to mutableListOf() }
        all.forEach { activityDb ->
            fun addRecursive(parentActivityDb: ActivityDb) {
                val childrenActivitiesDb =
                    all.filter { it.parent_id == parentActivityDb.id }
                resMap[activityDb.id]!!.addAll(childrenActivitiesDb)
                childrenActivitiesDb.forEach { addRecursive(it) }
            }
            addRecursive(activityDb)
        }
        return resMap
    }

    fun nextActivityColor(): ColorRgba {
        val activitiesColors: List<String> =
            activitiesDb.map { activityDb -> activityDb.colorRgba.toRgbaString() }
        for (color in colors) {
            if (!activitiesColors.contains(color.toRgbaString()))
                return color
        }
        return colors.random()
    }

    private var taskFolderIndexCache: Pair<List<TaskFolderDb>, Map<Int, TaskFolderDb>>? = null

    private fun taskFolderIndex(): Map<Int, TaskFolderDb> {
        val list = taskFoldersDbSorted
        val cached = taskFolderIndexCache
        if (cached != null && cached.first === list)
            return cached.second
        val index = list.associateBy { it.id }
        taskFolderIndexCache = list to index
        return index
    }

    fun taskFolderOrNull(id: Int): TaskFolderDb? =
        taskFolderIndex()[id]

    fun requireTaskFolder(id: Int): TaskFolderDb =
        taskFolderIndex()[id] ?: throw NoSuchElementException("TaskFolder $id not found")

    private var noteFolderIndexCache: Pair<List<NoteFolderDb>, Map<Int, NoteFolderDb>>? = null

    private fun noteFolderIndex(): Map<Int, NoteFolderDb> {
        val list = noteFoldersDb
        val cached = noteFolderIndexCache
        if (cached != null && cached.first === list)
            return cached.second
        val index = list.associateBy { it.id }
        noteFolderIndexCache = list to index
        return index
    }

    fun noteFolderOrNull(id: Int): NoteFolderDb? =
        noteFolderIndex()[id]

    fun requireNoteFolder(id: Int): NoteFolderDb =
        noteFolderIndex()[id] ?: throw NoSuchElementException("NoteFolder $id not found")

    fun checklistItems(listId: Int): List<ChecklistItemDb> =
        checklistItemsDb.filter { it.list_id == listId }

    private var checklistIndexCache: Pair<List<ChecklistDb>, Map<Int, ChecklistDb>>? = null

    private fun checklistIndex(): Map<Int, ChecklistDb> {
        val list = checklistsDb
        val cached = checklistIndexCache
        if (cached != null && cached.first === list)
            return cached.second
        val index = list.associateBy { it.id }
        checklistIndexCache = list to index
        return index
    }

    fun checklistOrNull(id: Int): ChecklistDb? =
        checklistIndex()[id]

    private var shortcutIndexCache: Pair<List<ShortcutDb>, Map<Int, ShortcutDb>>? = null

    private fun shortcutIndex(): Map<Int, ShortcutDb> {
        val list = shortcutsDb
        val cached = shortcutIndexCache
        if (cached != null && cached.first === list)
            return cached.second
        val index = list.associateBy { it.id }
        shortcutIndexCache = list to index
        return index
    }

    fun shortcutOrNull(id: Int): ShortcutDb? =
        shortcutIndex()[id]

    private var kvIndexCache: Pair<List<KvDb>, Map<String, KvDb>>? = null

    private fun kvIndex(): Map<String, KvDb> {
        val list = kvDb
        val cached = kvIndexCache
        if (cached != null && cached.first === list)
            return cached.second
        val index = list.associateBy { it.key }
        kvIndexCache = list to index
        return index
    }

    fun kvOrNull(key: KvDb.KEY): KvDb? =
        kvIndex()[key.name]

    fun kvStringOrNull(key: KvDb.KEY): String? =
        kvOrNull(key)?.value

    //
    // Test seam
    //
    // Lists are read-only outside `Cache`. Tests that need arbitrary in-memory
    // state (no DB) set it here; a null argument leaves that list unchanged.

    internal fun overrideListsForTesting(
        checklistsDb: List<ChecklistDb>? = null,
        checklistItemsDb: List<ChecklistItemDb>? = null,
        shortcutsDb: List<ShortcutDb>? = null,
        notesDb: List<NoteDb>? = null,
        noteFoldersDb: List<NoteFolderDb>? = null,
        kvDb: List<KvDb>? = null,
        tasksDb: List<TaskDb>? = null,
        taskFoldersDbSorted: List<TaskFolderDb>? = null,
        eventsDb: List<EventDb>? = null,
        eventTemplatesDbSorted: List<EventTemplateDb>? = null,
        repeatingsDb: List<RepeatingDb>? = null,
        activitiesDb: List<ActivityDb>? = null,
        firstIntervalDb: IntervalDb? = null,
        lastIntervalDb: IntervalDb? = null,
        todayTaskFolderDb: TaskFolderDb? = null,
        tomorrowTaskFolderDb: TaskFolderDb? = null,
        somedayTaskFolderDb: TaskFolderDb? = null,
    ) {
        checklistsDb?.let { this.checklistsDb = it }
        checklistItemsDb?.let { this.checklistItemsDb = it }
        shortcutsDb?.let { this.shortcutsDb = it }
        notesDb?.let { this.notesDb = it }
        noteFoldersDb?.let { this.noteFoldersDb = it }
        kvDb?.let { this.kvDb = it }
        tasksDb?.let { this.tasksDb = it }
        taskFoldersDbSorted?.let { this.taskFoldersDbSorted = it }
        eventsDb?.let { this.eventsDb = it }
        eventTemplatesDbSorted?.let { this.eventTemplatesDbSorted = it }
        repeatingsDb?.let { this.repeatingsDb = it }
        activitiesDb?.let { this.activitiesDb = it }
        firstIntervalDb?.let { this.firstIntervalDb = it }
        lastIntervalDb?.let { this.lastIntervalDb = it }
        todayTaskFolderDb?.let { this.todayTaskFolderDb = it }
        tomorrowTaskFolderDb?.let { this.tomorrowTaskFolderDb = it }
        somedayTaskFolderDb?.let { this.somedayTaskFolderDb = it }
    }

    //
    // Init

    // todo refactoring to ignore using cache
    private var lastInitScope: CoroutineScope? = null

    internal suspend fun init() {

        val scope = ioScope()
        lastInitScope?.cancel()
        lastInitScope = scope

        //
        // Database Lists

        checklistsDb = ChecklistDb.selectAsc()
        bumpVersion()
        ChecklistDb.selectAscFlow().onEachExIn(scope) { checklistsDb = it; bumpVersion() }

        checklistItemsDb = ChecklistItemDb.selectSorted()
        bumpVersion()
        ChecklistItemDb.selectSortedFlow().onEachExIn(scope) { checklistItemsDb = it; bumpVersion() }

        shortcutsDb = ShortcutDb.selectAsc()
        bumpVersion()
        ShortcutDb.selectAscFlow().onEachExIn(scope) { shortcutsDb = it; bumpVersion() }

        notesDb = NoteDb.selectAllSorted()
        bumpVersion()
        NoteDb.selectAllSortedFlow().onEachExIn(scope) { notesDb = it; bumpVersion() }

        noteFoldersDb = NoteFolderDb.selectAllSorted()
        bumpVersion()
        NoteFolderDb.selectAllSortedFlow().onEachExIn(scope) { noteFoldersDb = it; bumpVersion() }

        kvDb = KvDb.selectAll()
        bumpVersion()
        KvDb.selectAllFlow().onEachExIn(scope) { kvDb = it; bumpVersion() }

        tasksDb = TaskDb.selectAsc()
        bumpVersion()
        TaskDb.selectAscFlow().onEachExIn(scope) { tasksDb = it; bumpVersion() }

        val taskFoldersDbSortedLocal = TaskFolderDb.selectAllSorted()
        taskFoldersDbSorted = taskFoldersDbSortedLocal
        bumpVersion()
        taskFoldersDbSortedLocal.firstOrNull { it.isToday }?.let { todayTaskFolderDb = it }
        taskFoldersDbSortedLocal.firstOrNull { it.isTomorrow }?.let { tomorrowTaskFolderDb = it }
        taskFoldersDbSortedLocal.firstOrNull { it.isSomeday }?.let { somedayTaskFolderDb = it }
        TaskFolderDb.selectAllSortedFlow().onEachExIn(scope) { taskFoldersDbSorted_ ->
            taskFoldersDbSorted = taskFoldersDbSorted_
            bumpVersion()
            taskFoldersDbSorted_.firstOrNull { it.isToday }?.let { todayTaskFolderDb = it }
            taskFoldersDbSorted_.firstOrNull { it.isTomorrow }?.let { tomorrowTaskFolderDb = it }
            taskFoldersDbSorted_.firstOrNull { it.isSomeday }?.let { somedayTaskFolderDb = it }
        }

        eventsDb = EventDb.selectAscByTime()
        bumpVersion()
        EventDb.selectAscByTimeFlow().onEachExIn(scope) { eventsDb = it; bumpVersion() }

        eventTemplatesDbSorted = EventTemplateDb.selectAscSorted()
        bumpVersion()
        EventTemplateDb.selectAscSortedFlow().onEachExIn(scope) { eventTemplatesDbSorted = it; bumpVersion() }

        repeatingsDb = RepeatingDb.selectAsc()
        bumpVersion()
        RepeatingDb.selectAscFlow().onEachExIn(scope) { repeatingsDb = it; bumpVersion() }

        activitiesDb = ActivityDb.selectAll()
        bumpVersion()
        ActivityDb.selectAllFlow().onEachExIn(scope) { activitiesDb = it; bumpVersion() }

        //
        // Late Init

        IntervalDb.selectAsc(limit = 1).firstOrNull()?.let { firstIntervalDb = it }
        bumpVersion()
        IntervalDb.selectAscFlow(limit = 1).filter { it.isNotEmpty() }
            .onEachExIn(scope) { firstIntervalDb = it.first(); bumpVersion() }

        IntervalDb.selectDesc(limit = 1).firstOrNull()?.let { lastIntervalDb = it }
        bumpVersion()
        IntervalDb.selectDescFlow(limit = 1).filter { it.isNotEmpty() }
            .onEachExIn(scope) { lastIntervalDb = it.first(); bumpVersion() }
    }
}
