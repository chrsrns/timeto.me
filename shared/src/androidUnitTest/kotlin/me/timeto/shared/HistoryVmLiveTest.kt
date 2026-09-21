package me.timeto.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import me.timeto.shared.vm.history.HistoryVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryVmLiveTest {

    @Test
    fun intervalInsert_reselectsDaysUi() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = time() - 300)
        refreshCache()

        val vm = HistoryVm()
        try {
            // init does selectAndUpdate(-1) asynchronously on ioScope
            withTimeout(10_000) {
                while (vm.state.value.daysUi.isEmpty()) delay(20)
            }
            assertEquals(
                listOf(1),
                vm.state.value.daysUi.flatMap { it.intervalsDb.map { i -> i.id } },
            )

            // IntervalDb.anyChangeFlow().drop(1) -> reselect on change
            insertIntervalSq(id = 2, time = time() - 100)
            withTimeout(10_000) {
                while (!vm.state.value.daysUi
                        .flatMap { it.intervalsDb.map { i -> i.id } }
                        .contains(2)
                ) delay(20)
            }
        } finally {
            vm.onDestroy()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun restartIfLess1Min_recentInterval_invokesCallback() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            initTestDb()
            insertActivitySq(id = 1)
            insertIntervalSq(id = 1, time = time())
            refreshCache()

            val vm = HistoryVm()
            try {
                val latch = CompletableDeferred<Unit>()
                vm.restartDaysUiIfLess1Min { latch.complete(Unit) }
                withTimeout(5_000) { latch.await() }
                assertTrue(latch.isCompleted)
            } finally {
                vm.onDestroy()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun restartIfLess1Min_oldInterval_skipsRestart() = runBlocking {
        initTestDb()
        insertActivitySq(id = 1)
        insertIntervalSq(id = 1, time = time() - 120)
        refreshCache()

        val vm = HistoryVm()
        try {
            val latch = CompletableDeferred<Unit>()
            vm.restartDaysUiIfLess1Min { latch.complete(Unit) }
            delay(500)
            assertFalse(latch.isCompleted)
        } finally {
            vm.onDestroy()
        }
    }
}
