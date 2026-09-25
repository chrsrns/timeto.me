package me.timeto.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.vm.activity_form.ActivityFormVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityFormVmTest {

    private val symbol = Symbol.Icon.IconEnum.inbox.toIcon()

    @Test
    fun save_insert_persistsAlarmMode() = runBlocking {
        initTestDb()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            refreshCache()

            val vm = ActivityFormVm(null)
            vm.setName("Alarm Activity")
            vm.setSymbol(symbol)
            vm.setAlarmMode(true)

            val done = CompletableDeferred<ActivityDb>()
            vm.save(TestDialogsManager()) { done.complete(it) }
            val saved = withTimeout(10_000) { done.await() }

            assertEquals(1, saved.alarm_mode)
            assertEquals(1, ActivityDb.selectByIdOrNull(saved.id)!!.alarm_mode)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun save_insert_inheritStoresNull() = runBlocking {
        initTestDb()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            refreshCache()

            val vm = ActivityFormVm(null)
            vm.setName("Inherit Activity")
            vm.setSymbol(symbol)

            val done = CompletableDeferred<ActivityDb>()
            vm.save(TestDialogsManager()) { done.complete(it) }
            val saved = withTimeout(10_000) { done.await() }

            assertNull(saved.alarm_mode)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun save_edit_updatesAlarmMode() = runBlocking {
        initTestDb()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val activityDb = insertActivitySq(id = 1, name = "Work", alarmMode = 1)
            refreshCache()

            val vm = ActivityFormVm(activityDb)
            assertEquals(true, vm.state.value.alarmMode)

            vm.setAlarmMode(false)

            val done = CompletableDeferred<ActivityDb>()
            vm.save(TestDialogsManager()) { done.complete(it) }
            withTimeout(10_000) { done.await() }

            assertEquals(0, ActivityDb.selectByIdOrNull(1)!!.alarm_mode)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun save_edit_keepsAlarmModeWhenUnchanged() = runBlocking {
        initTestDb()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val activityDb = insertActivitySq(id = 1, name = "Work", alarmMode = 1)
            refreshCache()

            val vm = ActivityFormVm(activityDb)
            vm.setName("Work Renamed")

            val done = CompletableDeferred<ActivityDb>()
            vm.save(TestDialogsManager()) { done.complete(it) }
            withTimeout(10_000) { done.await() }

            assertEquals(1, ActivityDb.selectByIdOrNull(1)!!.alarm_mode)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
