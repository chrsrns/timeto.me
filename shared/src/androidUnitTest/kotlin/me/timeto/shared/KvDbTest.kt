package me.timeto.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.KvDb.Companion.asAlarmSnoozeIntervalId
import me.timeto.shared.db.KvDb.Companion.asAlarmSnoozeSeconds
import me.timeto.shared.db.KvDb.Companion.asAlarmSnoozeUntil
import me.timeto.shared.db.KvDb.Companion.asDayStartOffsetSeconds
import me.timeto.shared.db.KvDb.Companion.asTimerExpiredRepeatSeconds
import me.timeto.shared.db.KvDb.Companion.isAlarmModeDefaultEnabled
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
    fun isSendingReports_malformedValue_defaults() {
        // Non-numeric stored value falls back to flavor default, no crash
        assertTrue(KvDb("k", "abc").isSendingReports())
    }

    @Test
    fun selectIntOrNullFlow_malformed_emitsNull() = runBlocking {
        initTestDb()
        KvDb.KEY.RATE_TIME.upsertString("abc")
        assertNull(KvDb.KEY.RATE_TIME.selectIntOrNullFlow().first())
    }

    @Test
    fun asTimerExpiredRepeatSeconds_validatesMinutes() {
        assertEquals(0, null.asTimerExpiredRepeatSeconds())
        assertEquals(0, KvDb("k", "59").asTimerExpiredRepeatSeconds())
        assertEquals(0, KvDb("k", "90").asTimerExpiredRepeatSeconds())
        assertEquals(120, KvDb("k", "120").asTimerExpiredRepeatSeconds())
    }

    @Test
    fun asAlarmSnoozeSeconds_defaultsAndValidates() {
        assertEquals(300, null.asAlarmSnoozeSeconds())
        assertEquals(300, KvDb("k", "abc").asAlarmSnoozeSeconds())
        assertEquals(300, KvDb("k", "59").asAlarmSnoozeSeconds())
        assertEquals(300, KvDb("k", "90").asAlarmSnoozeSeconds())
        assertEquals(300, KvDb("k", "0").asAlarmSnoozeSeconds())
        assertEquals(60, KvDb("k", "60").asAlarmSnoozeSeconds())
        assertEquals(300, KvDb("k", "300").asAlarmSnoozeSeconds())
        assertEquals(3_600, KvDb("k", "3600").asAlarmSnoozeSeconds())
    }

    @Test
    fun isAlarmModeDefaultEnabled_defaultsFalse() {
        assertFalse(null.isAlarmModeDefaultEnabled())
        assertFalse(KvDb("k", "abc").isAlarmModeDefaultEnabled())
        assertFalse(KvDb("k", "0").isAlarmModeDefaultEnabled())
        assertTrue(KvDb("k", "1").isAlarmModeDefaultEnabled())
    }

    @Test
    fun asAlarmSnoozeUntil_defaultsZero() {
        assertEquals(0, null.asAlarmSnoozeUntil())
        assertEquals(0, KvDb("k", "abc").asAlarmSnoozeUntil())
        assertEquals(1_700_000_000, KvDb("k", "1700000000").asAlarmSnoozeUntil())
    }

    @Test
    fun asAlarmSnoozeIntervalId_nullWhenAbsent() {
        assertNull(null.asAlarmSnoozeIntervalId())
        assertNull(KvDb("k", "abc").asAlarmSnoozeIntervalId())
        assertEquals(7, KvDb("k", "7").asAlarmSnoozeIntervalId())
    }

    @Test
    fun alarmKeys_roundtrip() = runBlocking {
        initTestDb()

        KvDb.KEY.ALARM_MODE_DEFAULT.upsertBoolean(true)
        assertTrue(KvDb.KEY.ALARM_MODE_DEFAULT.selectOrNull().isAlarmModeDefaultEnabled())

        KvDb.KEY.ALARM_SNOOZE_SECONDS.upsertInt(600)
        assertEquals(600, KvDb.KEY.ALARM_SNOOZE_SECONDS.selectOrNull().asAlarmSnoozeSeconds())

        KvDb.KEY.ALARM_SNOOZE_UNTIL.upsertInt(1_700_000_000)
        assertEquals(1_700_000_000, KvDb.KEY.ALARM_SNOOZE_UNTIL.selectOrNull().asAlarmSnoozeUntil())

        KvDb.KEY.ALARM_SNOOZE_INTERVAL_ID.upsertInt(3)
        assertEquals(3, KvDb.KEY.ALARM_SNOOZE_INTERVAL_ID.selectOrNull().asAlarmSnoozeIntervalId())
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
