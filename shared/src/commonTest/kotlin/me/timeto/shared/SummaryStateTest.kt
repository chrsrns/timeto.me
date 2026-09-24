package me.timeto.shared

import kotlinx.datetime.LocalDate
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.vm.summary.SummaryVm
import kotlin.test.Test
import kotlin.test.assertEquals

class SummaryStateTest {

    @Test
    fun dateTitle_singleDay() {
        val state = testState(
            start = day(2026, 9, 10),
            finish = day(2026, 9, 10),
        )
        assertEquals("10 Sep", state.dateTitle)
    }

    @Test
    fun dateTitle_sameMonth() {
        val state = testState(
            start = day(2026, 9, 10),
            finish = day(2026, 9, 15),
        )
        assertEquals("10-15 Sep", state.dateTitle)
    }

    @Test
    fun dateTitle_crossMonth() {
        val state = testState(
            start = day(2026, 9, 10),
            finish = day(2026, 10, 15),
        )
        assertEquals("10 Sep - 15 Oct", state.dateTitle)
    }

    @Test
    fun barsTimeRows_evensPlusZero() {
        val state = testState(
            start = day(2026, 9, 10),
            finish = day(2026, 9, 10),
        )
        assertEquals(
            listOf("02", "04", "06", "08", "10", "12", "14", "16", "18", "20", "22", "00"),
            state.barsTimeRows,
        )
    }
}

private fun testState(
    start: UnixTime,
    finish: UnixTime,
): SummaryVm.State {
    // State.minPickerTime reads the lateinit cache entry
    Cache.overrideListsForTesting(firstIntervalDb = IntervalDb(
        id = 1,
        time = 1_000_000,
        activityId = 1,
        note = null,
    ))
    return SummaryVm.State(
        pickerTimeStart = start,
        pickerTimeFinish = finish,
        activitiesUi = emptyList(),
        daysBarsUi = emptyList(),
    )
}

private fun day(year: Int, month: Int, day: Int): UnixTime =
    UnixTime.byLocalDay(LocalDate(year, month, day).toEpochDays().toInt())
