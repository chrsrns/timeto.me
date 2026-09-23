package me.timeto.shared

import me.timeto.shared.vm.timer_picker.TimerPickerVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimerPickerTest {

    @Test
    fun pickerItems_sortedAndUnique() {
        val items = TimerPickerVm(
            initSeconds = 3_600,
            hints = emptyList(),
        ).state.value.pickerItemsUi
        val seconds = items.map { it.seconds }
        assertEquals(seconds.sorted(), seconds)
        assertEquals(seconds.toSet().size, seconds.size)
    }

    @Test
    fun pickerItems_initSecondsNotInRanges_appended() {
        val items = TimerPickerVm(
            initSeconds = 61,
            hints = emptyList(),
        ).state.value.pickerItemsUi
        assertEquals(61, items.map { it.seconds }[1])
        assertEquals("1 min", items[0].title)
    }

    @Test
    fun pickerItems_initSecondsInRanges_noDuplicate() {
        val withDup = TimerPickerVm(
            initSeconds = 60,
            hints = emptyList(),
        ).state.value.pickerItemsUi
        val withoutDup = TimerPickerVm(
            initSeconds = 61,
            hints = emptyList(),
        ).state.value.pickerItemsUi
        assertEquals(withDup.size + 1, withoutDup.size)
        assertEquals(1, withDup.count { it.seconds == 60 })
    }

    @Test
    fun pickerItems_titleFormats() {
        val items = TimerPickerVm(
            initSeconds = 3_900,
            hints = emptyList(),
        ).state.value.pickerItemsUi
        val bySeconds = items.associateBy { it.seconds }
        assertEquals("1 min", bySeconds[60]!!.title)
        assertEquals("10 min", bySeconds[600]!!.title)
        assertEquals("1 h", bySeconds[3_600]!!.title)
        assertEquals("1 : 05", bySeconds[3_900]!!.title)
        assertEquals("24 h", bySeconds[86_400]!!.title)
    }

    @Test
    fun pickerItems_coverDocumentedRanges() {
        val seconds = TimerPickerVm(
            initSeconds = 60,
            hints = emptyList(),
        ).state.value.pickerItemsUi.map { it.seconds }.toSet()
        // (1..10)*60 -> 60..600 by 60
        (1..10).forEach { assertTrue(it * 60 in seconds, "min=${it * 60}") }
        // (1..10)*(600+it*300) -> 900..3600 by 300
        (1..10).forEach { assertTrue(600 + it * 300 in seconds, "s=${600 + it * 300}") }
        // (1..138)*(3600+it*600) -> 4200..86400 by 600
        (1..138).forEach { assertTrue(3_600 + it * 600 in seconds, "s=${3_600 + it * 600}") }
    }

    @Test
    fun hintsUi_mapsHints() {
        val hints = TimerPickerVm(
            initSeconds = 60,
            hints = listOf(300, 3_600),
        ).state.value.hintsUi
        assertEquals(listOf(300, 3_600), hints.map { it.timer })
        hints.forEach { assertTrue(it.title.isNotBlank()) }
    }
}
