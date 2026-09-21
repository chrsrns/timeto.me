package me.timeto.shared

import kotlinx.coroutines.runBlocking
import me.timeto.shared.db.db
import me.timeto.shared.vm.color_picker.ColorPickerExampleUi
import me.timeto.shared.vm.color_picker.ColorPickerExamplesUi
import me.timeto.shared.vm.color_picker.ColorPickerVm
import me.timeto.shared.vm.symbol.SymbolPickerVm
import me.timeto.shared.vm.timer_picker.TimerPickerVm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pickers must hold local state only and perform no DB writes;
 * values are returned to the caller through onDone/onPick callbacks.
 * EmojiPickerVm is excluded — it needs the platform `getResourceContent`
 * actual, which is covered by the resource-tier task.
 */
class PickersPurityTest {

    @Test
    fun pickers_holdLocalState_onlyNoDbWrites() = runBlocking {
        initTestDb()

        // ColorPickerVm — local color selection
        val colorVm = ColorPickerVm(
            ColorPickerExamplesUi(
                mainExampleUi = ColorPickerExampleUi("main", ColorRgba(1, 2, 3)),
                secondaryHeader = "secondary",
                secondaryExamplesUi = emptyList(),
            )
        )
        colorVm.setColorRgba(ColorRgba(9, 9, 9))
        assertEquals(ColorRgba(9, 9, 9), colorVm.state.value.colorRgba)

        // SymbolPickerVm — chunked icon list, local only
        val symbolVm = SymbolPickerVm()
        assertTrue(symbolVm.state.value.symbolChunks.isNotEmpty())

        // TimerPickerVm — items built from init args
        val timerVm = TimerPickerVm(initSeconds = 3_600, hints = listOf(300))
        assertTrue(timerVm.state.value.pickerItemsUi.isNotEmpty())
        assertEquals(300, timerVm.state.value.hintsUi.first().timer)

        // DaytimeUi — pure value math
        assertEquals(
            DaytimeUi(hour = 10, minute = 30).seconds,
            DaytimeUi.byDaytime(37_800).seconds,
        )

        // No table touched
        assertTrue(db.kVQueries.selectAll().executeAsList().isEmpty())
        assertTrue(db.shortcutQueries.selectAsc().executeAsList().isEmpty())
        assertTrue(db.activityQueries.selectAll().executeAsList().isEmpty())
        assertTrue(db.intervalQueries.selectCount().executeAsOne() == 0L)
    }
}
