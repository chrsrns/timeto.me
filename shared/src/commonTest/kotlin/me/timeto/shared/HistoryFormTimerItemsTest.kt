package me.timeto.shared

import me.timeto.shared.vm.history.form.makeTimerItemsUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryFormTimerItemsTest {

    @Test
    fun grid_neighborsAroundSelectedTime() {
        // 10-minute aligned so all three grids anchor on it
        val selectedTime = (time() - 200_000) - ((time() - 200_000) % 600)
        val items = makeTimerItemsUi(selectedTime).map { it.time }

        assertTrue(items.contains(selectedTime))
        // +-1..9 minute grid
        assertTrue(items.contains(selectedTime - 60))
        assertTrue(items.contains(selectedTime + 9 * 60))
        // +-1..11 five-minute grid
        assertTrue(items.contains(selectedTime - 5 * 60))
        assertTrue(items.contains(selectedTime + 11 * 5 * 60))
        // +-1..143 ten-minute grid
        assertTrue(items.contains(selectedTime - 10 * 60))
        assertTrue(items.contains(selectedTime + 143 * 10 * 60))
        // Every item is a whole-minute offset from the selected time
        assertTrue(items.all { (it - selectedTime) % 60 == 0 })
        // 570s is in none of the three grids (>9min, not x5/x10-aligned)
        assertTrue(items.none { (it - selectedTime) == 570 })
        assertTrue(items.none { (it - selectedTime) == -570 })
    }

    @Test
    fun sorted_allNotInFuture() {
        val items = makeTimerItemsUi(time() - 50_000).map { it.time }
        assertEquals(items.sorted(), items)
        assertTrue(items.all { it <= time() })
    }

    @Test
    fun futureItemsFiltered_whenSelectedIsNow() {
        val now = time()
        val items = makeTimerItemsUi(now).map { it.time }
        assertTrue(items.contains(now))
        assertTrue(items.none { it > now })
        assertTrue(items.none { it == now + 60 })
    }
}
