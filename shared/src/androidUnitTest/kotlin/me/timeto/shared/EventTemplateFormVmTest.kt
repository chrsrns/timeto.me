package me.timeto.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.EventTemplateDb
import me.timeto.shared.vm.events.templates.EventTemplateFormVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventTemplateFormVmTest {

    @Test
    fun save_guardsInOrder_thenPersists() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1, name = "Work")
        refreshCache()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val vm = EventTemplateFormVm(initEventTemplateDb = null)
            val dialogs = TestDialogsManager()
            val noop = CompletableDeferred<Unit>()

            // Guard order in save(): daytime -> text -> activity -> timer
            vm.save(dialogs) { }
            assertEquals("Time is not set", dialogs.nextAlert())

            vm.setDaytime(DaytimeUi(hour = 9, minute = 30))
            vm.save(dialogs) { }
            assertEquals("Text is empty", dialogs.nextAlert())

            vm.setText("standup")
            vm.save(dialogs) { }
            assertEquals("Activity not selected", dialogs.nextAlert())

            vm.setActivity(Cache.activitiesDb.first())
            vm.save(dialogs) { }
            assertEquals("Timer not selected", dialogs.nextAlert())

            vm.setTimer(600)
            vm.save(dialogs) { noop.complete(Unit) }
            withTimeout(3_000) { noop.await() }
            assertTrue(dialogs.alerts.isEmpty)

            val templateDb = EventTemplateDb.selectAscSorted().single()
            assertEquals("standup", templateDb.text.textFeatures().textNoFeatures)
            assertEquals((9 * 3_600) + (30 * 60), templateDb.daytime)
            vm.onDestroy()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun TestDialogsManager.nextAlert(): String =
        withTimeout(3_000) { alerts.receive() }
}
