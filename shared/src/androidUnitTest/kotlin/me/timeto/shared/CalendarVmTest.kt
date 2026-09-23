package me.timeto.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.EventDb
import me.timeto.shared.db.RepeatingDb
import me.timeto.shared.db.TaskDb
import me.timeto.shared.vm.calendar.CalendarVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarVmTest {

    private suspend fun awaitMonths(vm: CalendarVm): List<CalendarVm.Month> =
        withTimeout(3_000) {
            while (vm.state.value.months.isEmpty()) delay(20)
            vm.state.value.months
        }

    private fun List<CalendarVm.Month>.day(unixDay: Int): CalendarVm.Month.Day =
        flatMap { it.weeks.flatten() }.filterNotNull().first { it.unixDay == unixDay }

    private suspend fun insertRepeating(
        text: String,
        inCalendar: Boolean,
        lastDay: Int = UnixTime().localDay - 1,
    ) = RepeatingDb.insertWithValidationEx(
        text = text,
        period = RepeatingDb.Period.EveryNDays(1),
        lastDay = lastDay,
        daytime = null,
        isImportant = false,
        inCalendar = inCalendar,
    )

    @Test
    fun previews_eventsAndRepeatings_noTasks() = runBlocking {
        initTestDb()
        seedTaskFolders()
        refreshCache()
        val today = UnixTime().localDay

        EventDb.insertWithValidation(
            text = "evt",
            localTime = UnixTime.byLocalDay(today).inSeconds(10 * 3_600).time,
        )
        insertRepeating(text = "rep", inCalendar = true)
        TaskDb.insertWithValidation_transactionRequired(
            folder = Cache.todayTaskFolderDb,
            text = "tasktxt",
        )

        val vm = CalendarVm()
        val months = awaitMonths(vm)
        val previews = months.day(today).previews

        // events first, then repeatings, padded to 3
        assertEquals(listOf("evt", "rep", ""), previews)
        assertFalse(previews.any { "tasktxt" in it })
        vm.onDestroy()
    }

    @Test
    fun months_span25AndPreviewTruncation() = runBlocking {
        initTestDb()
        val today = UnixTime().localDay
        val dayStart = UnixTime.byLocalDay(today)

        (1..4).forEach { i ->
            EventDb.insertWithValidation(
                text = "e$i",
                localTime = dayStart.inSeconds(i * 3_600).time,
            )
        }

        val vm = CalendarVm()
        val months = awaitMonths(vm)

        // calendarYears = 2 -> months 0..24
        assertEquals(25, months.size)

        // > 3 previews -> take(2) + "+N"
        assertEquals(listOf("e1", "e2", "+2"), months.day(today).previews)

        // empty day -> padded to 3 with ""
        assertEquals(listOf("", "", ""), months.day(today + 1).previews)
        vm.onDestroy()
    }

    @Test
    fun previews_inCalendarFilter() = runBlocking {
        initTestDb()
        val today = UnixTime().localDay

        insertRepeating(text = "hidden", inCalendar = false)
        insertRepeating(text = "shown", inCalendar = true)

        val vm = CalendarVm()
        val months = awaitMonths(vm)
        val allPreviews = months.flatMap { it.weeks.flatten() }.filterNotNull().flatMap { it.previews }

        assertFalse(allPreviews.any { "hidden" in it })
        assertTrue(allPreviews.any { "shown" in it })
        // EveryNDays(1) with last_day=today-1 -> next day is today
        assertEquals(listOf("shown", "", ""), months.day(today).previews)
        assertTrue("shown" in months.day(today + 1).previews)
        vm.onDestroy()
    }
}
