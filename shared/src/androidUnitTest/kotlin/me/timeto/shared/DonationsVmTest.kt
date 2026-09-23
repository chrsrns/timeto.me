package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.KvDb
import me.timeto.shared.vm.donations.DonationsVm
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Partial coverage: activate() performs a real Ktor GET with no injection
// seam. The happy path and status=="error" branch are not executable
// offline; what is provable is the contract that an activation attempt
// always ends with an alert and resets isActivationInProgress.
class DonationsVmTest {

    @Test
    fun activate_alwaysAlertsAndClearsProgress(): Unit = runBlocking {
        initTestDb()
        val dialogs = TestDialogsManager()
        val vm = DonationsVm()
        try {
            vm.activate("not-an-email", dialogs)

            withTimeout(30_000) { dialogs.alerts.receive() }
            withTimeout(30_000) { vm.state.first { !it.isActivationInProgress } }
        } finally {
            vm.onDestroy()
        }
    }

    @Test
    fun onTapAnotherTime_writesNegativeDonationsTime(): Unit = runBlocking {
        initTestDb()
        val vm = DonationsVm()
        try {
            vm.onTapAnotherTime()
            withTimeout(5_000) {
                while (KvDb.KEY.DONATIONS_TIME.selectOrNull() == null)
                    delay(50)
            }
            val stored = KvDb.KEY.DONATIONS_TIME.selectOrNull()!!.value.toInt()
            assertTrue(stored < 0)
            assertTrue(-stored <= time())
        } finally {
            vm.onDestroy()
        }
    }
}
