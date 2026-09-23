package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.KvDb
import me.timeto.shared.vm.privacy.PrivacyVm
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyVmTest {

    @Test
    fun defaultsAndToggle() = runBlocking {
        initTestDb() // flavor = null -> non-fdroid -> default enabled
        val vm = PrivacyVm()
        try {
            assertTrue(vm.state.value.isSendingReportsEnabled)

            vm.setIsSendingReports(false)
            withTimeout(5_000) { vm.state.first { !it.isSendingReportsEnabled } }
            assertTrue(KvDb.KEY.IS_SENDING_REPORTS.selectOrNull()!!.value.toInt() < 0)

            vm.setIsSendingReports(true)
            withTimeout(5_000) { vm.state.first { it.isSendingReportsEnabled } }
            assertTrue(KvDb.KEY.IS_SENDING_REPORTS.selectOrNull()!!.value.toInt() > 0)
        } finally {
            vm.onDestroy()
        }
    }
}
