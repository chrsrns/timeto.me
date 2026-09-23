package me.timeto.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.RepeatingDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.vm.repeatings.form.RepeatingFormVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepeatingFormVmSaveDbTest {

    @Test
    fun save_everyNDaysOne_anchorsYesterday_materializesToday() = runBlocking {
        initTestDb()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            seedTaskFolders()
            val activityDb = insertActivitySq(id = 1, name = "Health")
            refreshCache()
            val today = UnixTime().localDay

            val vm = RepeatingFormVm(null)
            vm.setText("med")
            vm.setPeriod(RepeatingDb.Period.EveryNDays(1))
            vm.setDaytime(DaytimeUi(hour = 12, minute = 0))
            vm.setActivity(activityDb)
            vm.setTimerSeconds(1800)

            val done = CompletableDeferred<Unit>()
            vm.save(TestDialogsManager()) { done.complete(Unit) }
            withTimeout(10_000) { done.await() }

            val repeatingDb = RepeatingDb.selectAsc().first()
            // Anchor last_day = today - 1 -> getNextDay() = today -> syncTodaySafe
            // materializes a task immediately and bumps last_day back to today.
            assertEquals(today, repeatingDb.last_day)
            val taskDb = TaskDb.selectAsc().first()
            assertEquals(TaskFolderDb.ID_TODAY, taskDb.folder_id)
            assertEquals(repeatingDb.id, taskDb.text.textFeatures().fromRepeating?.id)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun save_everyNDaysTwo_anchorsToday_noMaterialization() = runBlocking {
        initTestDb()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            seedTaskFolders()
            val activityDb = insertActivitySq(id = 1, name = "Health")
            refreshCache()
            val today = UnixTime().localDay

            val vm = RepeatingFormVm(null)
            vm.setText("med")
            vm.setPeriod(RepeatingDb.Period.EveryNDays(2))
            vm.setDaytime(DaytimeUi(hour = 12, minute = 0))
            vm.setActivity(activityDb)
            vm.setTimerSeconds(1800)

            val done = CompletableDeferred<Unit>()
            vm.save(TestDialogsManager()) { done.complete(Unit) }
            withTimeout(10_000) { done.await() }

            // Anchor last_day = today -> getNextDay() = today + 2 -> nothing materialized.
            assertEquals(today, RepeatingDb.selectAsc().first().last_day)
            assertTrue(TaskDb.selectAsc().isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun save_edit_updatesIsImportantOnTasks_preservesLastDay() = runBlocking {
        initTestDb()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            seedTaskFolders()
            insertActivitySq(id = 1, name = "Health")
            refreshCache()
            val today = UnixTime().localDay

            RepeatingDb.insertWithValidationEx(
                text = "med", period = RepeatingDb.Period.EveryNDays(1), lastDay = today - 1,
                daytime = 43200, isImportant = false, inCalendar = true,
            )
            val repeatingId = RepeatingDb.selectAsc().first().id
            RepeatingDb.syncTodaySafe(DayStartOffsetUtils.getToday())

            val taskDb = TaskDb.selectAsc().first()
            assertFalse(taskDb.text.textFeatures().isImportant)

            // Re-fetch after sync: the VM binds a fresh row, not a pre-sync snapshot.
            val repeatingDb = RepeatingDb.selectAsc().first { it.id == repeatingId }

            val vm = RepeatingFormVm(repeatingDb)
            val activityDb = Cache.activitiesDb.first()
            vm.setActivity(activityDb)
            vm.setTimerSeconds(1800)
            vm.setIsImportant(true)

            val done = CompletableDeferred<Unit>()
            vm.save(TestDialogsManager()) { done.complete(Unit) }
            withTimeout(10_000) { done.await() }

            assertTrue(TaskDb.selectAsc().first().text.textFeatures().isImportant)
            // updateWithValidationEx writes last_day = last_day -> sync value preserved.
            assertEquals(today, RepeatingDb.selectAsc().first().last_day)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
