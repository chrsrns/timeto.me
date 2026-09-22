package me.timeto.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.EventDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.db.TaskFolderDb
import me.timeto.shared.vm.events.EventFormVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventFormVmSaveTest {

    @Test
    fun save_newEventToday_promotesToTask() = runBlocking {
        initTestDb()
        seedTaskFolders()
        refreshCache()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val today = UnixTime().localDay
            val vm = EventFormVm(
                initEventDb = null,
                initText = null,
                initTime = null,
            )
            vm.setText("meet")
            vm.setUnixDay(today)
            vm.setDaytime(DaytimeUi(hour = 10, minute = 0))

            val latch = CompletableDeferred<Unit>()
            vm.save(TestDialogsManager()) { latch.complete(Unit) }
            // onSuccess is dispatched via onUi before syncTodaySafe runs, so
            // poll the db outcome rather than the latch alone.
            withTimeout(3_000) {
                latch.await()
                while (TaskDb.selectAsc().isEmpty()) delay(20)
            }

            // save() runs syncTodaySafe -> the same-day event is promoted to a
            // task and removed from the event table.
            assertTrue(EventDb.selectAscByTime().isEmpty())
            val taskDb = TaskDb.selectAsc().single()
            assertEquals(TaskFolderDb.ID_TODAY, taskDb.folder_id)
            assertEquals("meet", taskDb.text.textFeatures().textNoFeatures)
            assertEquals(
                UnixTime.byLocalDay(today).inSeconds(10 * 3_600).time,
                taskDb.text.textFeatures().fromEvent!!.unixTime.time,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }
}
