package me.timeto.shared

import me.timeto.shared.db.RepeatingDb
import me.timeto.shared.vm.repeatings.list.toUiList
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatingListVmTest {

    private fun buildRepeating(
        id: Int,
        lastDay: Int,
        daytime: Int? = null,
    ): RepeatingDb = RepeatingDb(
        id = id, text = "r$id", last_day = lastDay,
        type_id = RepeatingDb.TYPE.EVERY_N_DAYS.id, value = "1",
        daytime = daytime, is_important = 0, in_calendar = 1,
    )

    @Test
    fun toUiList_ordersByNextDayThenDaytime() {
        // EveryNDays(1): getNextDay() = last_day + 1
        val a = buildRepeating(id = 100, lastDay = 10)                    // day 11, noDaytime
        val b = buildRepeating(id = 200, lastDay = 10)                    // day 11, noDaytime
        val c = buildRepeating(id = 150, lastDay = 10, daytime = 3600)    // day 11, daytime 1h
        val d = buildRepeating(id = 300, lastDay = 10, daytime = 1800)    // day 11, daytime 30min
        val e = buildRepeating(id = 50, lastDay = 20)                     // day 21

        val ordered = listOf(e, c, a, d, b).toUiList().map { it.repeatingDb.id }

        // Day 11 group first: noDaytime id desc (200, 100), then withDaytime asc (1800, 3600).
        // Day 21 group last.
        assertEquals(listOf(200, 100, 300, 150, 50), ordered)
    }
}
