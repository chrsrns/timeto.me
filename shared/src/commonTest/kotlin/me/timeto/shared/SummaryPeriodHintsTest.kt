package me.timeto.shared

import me.timeto.shared.db.IntervalDb
import me.timeto.shared.vm.summary.SummaryVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SummaryPeriodHintsTest {

    private fun makeState(
        start: UnixTime,
        finish: UnixTime,
    ) = SummaryVm.State(
        pickerTimeStart = start,
        pickerTimeFinish = finish,
        activitiesUi = emptyList(),
        daysBarsUi = emptyList(),
    )

    init {
        // minPickerTime reads firstIntervalDb eagerly on State construction
        val intervalDb = IntervalDb(id = 1, time = 0, activityId = 1, note = null)
        Cache.fillLateInit(intervalDb, intervalDb)
    }

    @Test
    fun periodHints_titlesAndRanges() {
        val now = UnixTime()
        val yesterday = now.inDays(-1)
        val hints = makeState(now, now).periodHints

        assertEquals(listOf("Today", "Yesterday", "7d", "30d"), hints.map { it.title })
        assertEquals(now.localDay, hints[0].pickerTimeStart.localDay)
        assertEquals(yesterday.localDay, hints[1].pickerTimeStart.localDay)
        assertEquals(yesterday.inDays(-6).localDay, hints[2].pickerTimeStart.localDay)
        assertEquals(yesterday.localDay, hints[2].pickerTimeFinish.localDay)
        assertEquals(yesterday.inDays(-29).localDay, hints[3].pickerTimeStart.localDay)
    }

    @Test
    fun today_selected_notCustom() {
        val now = UnixTime()
        val state = makeState(now, now)
        assertTrue(state.periodHints[0].isActive)
        assertFalse(state.isCustomPeriodSelected)
    }

    @Test
    fun yesterday_selected_notCustom() {
        val yesterday = UnixTime().inDays(-1)
        val state = makeState(yesterday, yesterday)
        assertTrue(state.periodHints[1].isActive)
        assertFalse(state.isCustomPeriodSelected)
    }

    @Test
    fun sevenDayRange_selected_notCustom() {
        val yesterday = UnixTime().inDays(-1)
        val state = makeState(yesterday.inDays(-6), yesterday)
        assertTrue(state.periodHints[2].isActive)
        assertFalse(state.isCustomPeriodSelected)
    }

    @Test
    fun unmatchedRange_isCustom() {
        val now = UnixTime()
        val state = makeState(now.inDays(-3), now)
        assertTrue(state.isCustomPeriodSelected)
        assertTrue(state.periodHints.none { it.isActive })
    }
}
