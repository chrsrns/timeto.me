package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.vm.calendar.CalendarDayVm
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarDayVmTest {

    @Test
    fun inNote_dayDiffStrings() = runBlocking {
        initTestDb()
        val today = UnixTime().localDay

        fun noteAt(dayOffset: Int): String {
            val vm = CalendarDayVm(unixDay = today + dayOffset)
            val note = vm.state.value.inNote
            vm.onDestroy()
            return note
        }

        assertEquals("Yesterday", noteAt(-1))
        assertEquals("Today", noteAt(0))
        assertEquals("Tomorrow", noteAt(1))
        assertEquals("In 5 days", noteAt(5))
        assertEquals("5 days ago", noteAt(-5))
    }
}
