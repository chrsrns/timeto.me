package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.KvDb.Companion.isSendingReports
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportsGatingTest {

    @Test
    fun isSendingReports_missingKv_defaultsEnabledOnNonFdroid() = runBlocking {
        initTestDb() // flavor = null -> isFdroid = false
        assertTrue(KvDb.KEY.IS_SENDING_REPORTS.selectOrNull().isSendingReports())
    }

    @Test
    fun isSendingReports_storedValuePolarity() = runBlocking {
        initTestDb()
        KvDb.KEY.IS_SENDING_REPORTS.upsertInt(1)
        assertTrue(KvDb.KEY.IS_SENDING_REPORTS.selectOrNull().isSendingReports())
        KvDb.KEY.IS_SENDING_REPORTS.upsertInt(-1)
        assertFalse(KvDb.KEY.IS_SENDING_REPORTS.selectOrNull().isSendingReports())
    }

    @Test
    fun upsertIsSendingReports_storesSignedTime() = runBlocking {
        initTestDb()
        KvDb.upsertIsSendingReports(true)
        assertTrue(KvDb.KEY.IS_SENDING_REPORTS.selectOrNull()!!.value.toInt() > 0)
        KvDb.upsertIsSendingReports(false)
        assertTrue(KvDb.KEY.IS_SENDING_REPORTS.selectOrNull()!!.value.toInt() < 0)
    }

    @Test
    fun reportApi_disabled_returnsWithoutThrowing() = runBlocking {
        initTestDb()
        KvDb.upsertIsSendingReports(false)
        // Gate check happens on ioScope; give it a moment, expect no crash.
        reportApi("test message")
        delay(300)
        assertFalse(KvDb.KEY.IS_SENDING_REPORTS.selectOrNull().isSendingReports())
    }
}
