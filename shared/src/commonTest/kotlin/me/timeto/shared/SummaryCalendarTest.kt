package me.timeto.shared

import me.timeto.shared.db.IntervalDb
import me.timeto.shared.vm.summary.SummaryCalendarVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SummaryCalendarTest {

    @Test
    fun buildCalendar_weeksAlwaysSevenDays() {
        val weeks = buildTestCalendar()
        weeks.forEach { week ->
            assertEquals(7, week.daysUi.size)
        }
    }

    @Test
    fun buildCalendar_firstWeekLeftPadded() {
        val firstDay = UnixTime().inDays(-30)
        val weeks = buildTestCalendar(startDaysAgo = 30)
        val leadingNulls = weeks.first().daysUi.takeWhile { it == null }.size
        assertEquals(firstDay.dayOfWeek(), leadingNulls)
    }

    @Test
    fun buildCalendar_lastWeekRightPadded() {
        val today = UnixTime()
        val weeks = buildTestCalendar(startDaysAgo = 30)
        val lastWeek = weeks.last().daysUi
        val trailingNulls = lastWeek.takeLastWhile { it == null }.size
        assertEquals(6 - today.dayOfWeek(), trailingNulls)
    }

    @Test
    fun buildCalendar_sundayClosesWeek() {
        val weeks = buildTestCalendar(startDaysAgo = 30)
        weeks.dropLast(1).forEach { week ->
            val lastDay = week.daysUi.last()
            assertEquals(6, lastDay!!.unixDay.let { UnixTime.byLocalDay(it).dayOfWeek() })
        }
    }

    @Test
    fun selectDate_firstTap_setsStartOnly() {
        val vm = testVm()
        val day = UnixTime().inDays(-5)
        var completed: Pair<UnixTime, UnixTime>? = null
        vm.selectDate(day) { s, f -> completed = s to f }
        assertNull(completed)
        assertEquals(setOf(day.localDay), vm.state.value.selectedDays)
    }

    @Test
    fun selectDate_secondTap_completesMinMax() {
        val vm = testVm()
        val dayA = UnixTime().inDays(-5)
        val dayB = UnixTime().inDays(-2)
        var completed: Pair<UnixTime, UnixTime>? = null
        vm.selectDate(dayA) { s, f -> completed = s to f }
        vm.selectDate(dayB) { s, f -> completed = s to f }
        assertEquals(dayA.time, completed!!.first.time)
        assertEquals(dayB.time, completed!!.second.time)
    }

    @Test
    fun selectDate_secondTapEarlier_swapsOrder() {
        val vm = testVm()
        val dayA = UnixTime().inDays(-2)
        val dayB = UnixTime().inDays(-5)
        var completed: Pair<UnixTime, UnixTime>? = null
        vm.selectDate(dayA) { s, f -> completed = s to f }
        vm.selectDate(dayB) { s, f -> completed = s to f }
        assertEquals(dayB.time, completed!!.first.time)
        assertEquals(dayA.time, completed!!.second.time)
    }
}

private fun buildTestCalendar(
    startDaysAgo: Int = 30,
): List<SummaryCalendarVm.WeekUi> {
    Cache.firstIntervalDb = IntervalDb(
        id = 1,
        time = UnixTime().inDays(-startDaysAgo).time,
        activityId = 1,
        note = null,
    )
    return me.timeto.shared.vm.summary.buildCalendar()
}

private fun testVm(): SummaryCalendarVm {
    buildTestCalendar() // seeds Cache.firstIntervalDb
    val now = UnixTime()
    return SummaryCalendarVm(
        selectedStartTime = now,
        selectedFinishTime = now,
    )
}
