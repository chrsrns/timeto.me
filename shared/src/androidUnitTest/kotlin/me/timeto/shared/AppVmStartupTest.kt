package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.ChecklistDb
import me.timeto.shared.db.ChecklistItemDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.NoteFolderDb
import me.timeto.shared.db.RepeatingDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.db.db
import me.timeto.shared.vm.app.AppVm
import me.timeto.shared.vm.whats_new.WhatsNewVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVmStartupTest {

    private suspend fun awaitReady(vm: AppVm) {
        withTimeout(10_000) { vm.state.first { it.isAppReady } }
    }

    @Test
    fun init_freshDb_seedsThenReadyThenFlows(): Unit = runBlocking {
        initTestDb()
        resetCacheLateInit()
        val vm = AppVm()
        try {
            awaitReady(vm)

            // Seed + migrations complete by the time isAppReady flips.
            val folders = TaskFolderDb.selectAllSorted()
            assertTrue(folders.any { it.isToday })
            assertTrue(folders.any { it.isTomorrow })
            assertTrue(folders.any { it.isSomeday })
            assertEquals(7, ActivityDb.selectAll().size)
            assertEquals(listOf("Notes"), NoteFolderDb.selectAllSorted().map { it.name })
            assertEquals(
                WhatsNewVm.historyItemsUi.first().unixDay,
                KvDb.KEY.WHATS_NEW_CHECK_UNIX_DAY.selectOrNull()!!.value.toInt(),
            )

            // Flows are registered only after isAppReady: the backup flow
            // (drop(1)) forwards new values to state.
            AppVm.backupStateFlow.emit("backup done")
            withTimeout(5_000) { vm.state.first { it.backupMessage == "backup done" } }
        } finally {
            vm.onDestroy()
            AppVm.backupStateFlow.emit(null)
        }
    }

    @Test
    fun init_existingDb_skipsFillInitData() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = time(), activityId = 1)
        refreshCache()
        assertTrue(Cache.isLateInitInitialized())

        val vm = AppVm()
        try {
            awaitReady(vm)

            // fillInitData skipped: no Notes folder, no seeded activities,
            // no TODAY folder — but the July migration still ran.
            assertTrue(NoteFolderDb.selectAllSorted().isEmpty())
            assertEquals(1, ActivityDb.selectAll().size)
            val folders = TaskFolderDb.selectAllSorted()
            assertEquals(2, folders.size)
            assertTrue(folders.any { it.isTomorrow })
            assertTrue(folders.any { it.isSomeday })
        } finally {
            vm.onDestroy()
        }
    }

    @Test
    fun init_mayMigration_intGoalToTimer_idempotent() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, goalJson = "3600")
        insertActivitySq(id = 2, goalJson = "not-a-number")
        insertIntervalSq(id = 1, time = time(), activityId = 1)
        refreshCache()

        val vm = AppVm()
        try {
            awaitReady(vm)
        } finally {
            vm.onDestroy()
        }

        val activities = ActivityDb.selectAll()
        assertEquals(
            ActivityDb.GoalType.Timer(seconds = 3600),
            ActivityDb.GoalType.fromJson(activities.first { it.id == 1 }.goal_json!!),
        )
        // Non-int legacy values are left untouched.
        assertEquals("not-a-number", activities.first { it.id == 2 }.goal_json)

        // Re-run: converted value is not an int -> skipped, no double-convert.
        val vm2 = AppVm()
        try {
            awaitReady(vm2)
        } finally {
            vm2.onDestroy()
        }
        assertEquals(
            ActivityDb.GoalType.Timer(seconds = 3600),
            ActivityDb.GoalType.fromJson(ActivityDb.selectAll().first { it.id == 1 }.goal_json!!),
        )
    }

    @Test
    fun init_julyMigration_insertsMissingAndRenamesSmday() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = time(), activityId = 1)
        TaskFolderDb.insertNoValidation(
            id = TaskFolderDb.ID_TODAY, sort = 1, activityDb = null,
            name = "Today", symbol = Symbol.Icon.IconEnum.sun.toIcon(),
        )
        TaskFolderDb.insertNoValidation(
            id = 42, sort = 3, activityDb = null,
            name = "smday", symbol = Symbol.Icon.IconEnum.inbox.toIcon(),
        )
        val smdayFolder = TaskFolderDb.selectAllSorted().first { it.id == 42 }
        TaskDb.insertWithValidation("later", smdayFolder)
        refreshCache()

        val vm = AppVm()
        try {
            awaitReady(vm)
        } finally {
            vm.onDestroy()
        }

        val folders = TaskFolderDb.selectAllSorted()
        assertEquals(3, folders.size)
        assertTrue(folders.any { it.isTomorrow })
        assertTrue(folders.any { it.isSomeday })
        assertFalse(folders.any { it.id == 42 })
        // Rename moved the task's folder_id inside the same transaction.
        assertEquals(TaskFolderDb.ID_SOMEDAY, TaskDb.selectAsc().single().folder_id)

        // Idempotent re-run: still exactly 3 folders.
        val vm2 = AppVm()
        try {
            awaitReady(vm2)
        } finally {
            vm2.onDestroy()
        }
        assertEquals(3, TaskFolderDb.selectAllSorted().size)
    }

    @Test
    fun init_julyMigration_noDuplicateAfterSeed() = runBlocking {
        initTestDb()
        resetCacheLateInit() // full seed path already inserts all 3 folders

        val vm = AppVm()
        try {
            awaitReady(vm)
        } finally {
            vm.onDestroy()
        }
        assertEquals(3, TaskFolderDb.selectAllSorted().size)

        val vm2 = AppVm()
        try {
            awaitReady(vm2)
        } finally {
            vm2.onDestroy()
        }
        assertEquals(3, TaskFolderDb.selectAllSorted().size)
    }

    @Test
    fun init_dayChangeFlow_runsChecklistResetRepeatingAndTomorrowSync() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = time(), activityId = 1)
        seedTaskFolders()
        refreshCache()

        val todayDso = DayStartOffsetUtils.getToday()

        // Stale checklist: reset_day < today with a checked item.
        val checklist = ChecklistDb.insertWithValidation("stale", isResetOnDayStarts = true)
        ChecklistItemDb.insertWithValidation("i", checklist, isChecked = true)
        val itemId = ChecklistItemDb.selectSorted().last().id
        db.checklistQueries.updateResetDayById(reset_day = todayDso - 1, id = checklist.id)

        // Overdue repeating materializes a task.
        RepeatingDb.insertWithValidationEx(
            text = "rep", period = RepeatingDb.Period.EveryNDays(1),
            lastDay = todayDso - 1, daytime = null, isImportant = false, inCalendar = true,
        )

        // Yesterday-id task sitting in Tomorrow.
        val yesterdayTaskId = UnixTime().localDayStartTime() - 1
        db.taskQueries.insert(
            id = yesterdayTaskId, folder_id = TaskFolderDb.ID_TOMORROW, text = "old",
        )
        refreshCache()

        val vm = AppVm()
        try {
            awaitReady(vm)

            // buildTodayFlow emits on registration -> all three syncs ran once.
            withTimeout(5_000) {
                while (ChecklistItemDb.selectSorted().first { it.id == itemId }.check_time != 0)
                    delay(50)
            }
            assertEquals(
                todayDso,
                ChecklistDb.selectAsc().first { it.id == checklist.id }.reset_day,
            )
            withTimeout(5_000) {
                while (TaskDb.selectAsc().none { it.text.textFeatures().fromRepeating != null })
                    delay(50)
            }
            withTimeout(5_000) {
                while (
                    TaskDb.selectAsc().firstOrNull { it.id == yesterdayTaskId }
                        ?.folder_id != TaskFolderDb.ID_TODAY
                ) delay(50)
            }
        } finally {
            vm.onDestroy()
        }
    }

    @Test
    fun init_zenModeAllowed_docReadAndSetting(): Unit = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = time(), activityId = 1)
        seedTaskFolders()
        refreshCache()

        val vm = AppVm()
        try {
            awaitReady(vm)
            // Zen setting defaults true; guide not read -> not allowed.
            withTimeout(5_000) { vm.state.first { !it.isZenModeAllowed } }

            KvDb.KEY.DOC_FORCE_READ_TIME.upsertInt(time())
            withTimeout(5_000) { vm.state.first { it.isZenModeAllowed } }

            KvDb.KEY.ZEN_MODE_ENABLED.upsertInt(0)
            withTimeout(5_000) { vm.state.first { !it.isZenModeAllowed } }
        } finally {
            vm.onDestroy()
        }
    }
}
