package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.KvDb.Companion.asDayStartOffsetSeconds
import me.timeto.shared.db.KvDb.Companion.asTimerExpiredRepeatSeconds
import me.timeto.shared.db.KvDb.Companion.isSendingReports
import me.timeto.shared.db.KvDb.Companion.isZenModeEnabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KvDbTest {

    @Test
    fun asDayStartOffsetSeconds_defaults() {
        assertEquals(0, null.asDayStartOffsetSeconds())
        assertEquals(0, KvDb("k", "abc").asDayStartOffsetSeconds())
        assertEquals(3_600, KvDb("k", "3600").asDayStartOffsetSeconds())
        assertEquals(-3_600, KvDb("k", "-3600").asDayStartOffsetSeconds())
    }

    @Test
    fun isZenModeEnabled_defaultTrue() {
        assertTrue(null.isZenModeEnabled())
        assertTrue(KvDb("k", "1").isZenModeEnabled())
        assertFalse(KvDb("k", "0").isZenModeEnabled())
    }

    @Test
    fun isSendingReports_defaultByFlavor() {
        // Test SystemInfo flavor is null -> !isFdroid -> true
        assertTrue(null.isSendingReports())
        assertTrue(KvDb("k", "5").isSendingReports())
        assertFalse(KvDb("k", "0").isSendingReports())
        assertFalse(KvDb("k", "-5").isSendingReports())
    }

    @Test
    fun asTimerExpiredRepeatSeconds_validatesMinutes() {
        assertEquals(0, null.asTimerExpiredRepeatSeconds())
        assertEquals(0, KvDb("k", "59").asTimerExpiredRepeatSeconds())
        assertEquals(0, KvDb("k", "90").asTimerExpiredRepeatSeconds())
        assertEquals(120, KvDb("k", "120").asTimerExpiredRepeatSeconds())
    }

    @Test
    fun upsert_roundtrip() = runBlocking {
        initTestDb()
        assertNull(KvDb.KEY.DAY_START_OFFSET_SECONDS.selectOrNull())

        KvDb.KEY.DAY_START_OFFSET_SECONDS.upsertInt(3_600)
        assertEquals("3600", KvDb.KEY.DAY_START_OFFSET_SECONDS.selectStringOrNull())
        assertEquals(3_600, KvDb.KEY.DAY_START_OFFSET_SECONDS.selectOrNull().asDayStartOffsetSeconds())

        KvDb.KEY.ZEN_MODE_ENABLED.upsertBoolean(true)
        assertEquals("1", KvDb.KEY.ZEN_MODE_ENABLED.selectStringOrNull())
        KvDb.KEY.ZEN_MODE_ENABLED.upsertBoolean(false)
        assertEquals("0", KvDb.KEY.ZEN_MODE_ENABLED.selectStringOrNull())

        // upsert = INSERT OR REPLACE, not duplicate rows
        KvDb.KEY.DAY_START_OFFSET_SECONDS.upsertInt(7_200)
        assertEquals("7200", KvDb.KEY.DAY_START_OFFSET_SECONDS.selectStringOrNull())
        assertEquals(2, KvDb.selectAll().size)
    }

    @Test
    fun upsertIsSendingReports_storesSignedTime() = runBlocking {
        initTestDb()
        val now = time()

        KvDb.upsertIsSendingReports(true)
        val enabledValue = KvDb.KEY.IS_SENDING_REPORTS.selectStringOrNull()!!.toInt()
        assertTrue(enabledValue in now..(now + 5))
        assertTrue(KvDb.KEY.IS_SENDING_REPORTS.selectOrNull().isSendingReports())

        KvDb.upsertIsSendingReports(false)
        val disabledValue = KvDb.KEY.IS_SENDING_REPORTS.selectStringOrNull()!!.toInt()
        assertTrue(disabledValue in -(now + 5)..-now)
        assertFalse(KvDb.KEY.IS_SENDING_REPORTS.selectOrNull().isSendingReports())
    }
}
