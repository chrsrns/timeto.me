package me.timeto.shared

import me.timeto.shared.vm.symbol.SymbolLetterPickerUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SymbolLetterPickerTest {

    @Test
    fun validateLetter_blank_alertsNoCallback() {
        listOf("", " ", "   ").forEach { input ->
            val dialogs = FakeDialogsManager()
            var result: Symbol.Letter? = null
            SymbolLetterPickerUtils.validateLetter(input, dialogs) {
                result = it
            }
            assertEquals("Empty Symbol", dialogs.alertMessage, "input='$input'")
            assertNull(result, "input='$input'")
        }
    }

    @Test
    fun validateLetter_valid_callsSuccessWithTrimmed() {
        val dialogs = FakeDialogsManager()
        var result: Symbol.Letter? = null
        SymbolLetterPickerUtils.validateLetter("  A  ", dialogs) {
            result = it
        }
        assertNull(dialogs.alertMessage)
        assertEquals(Symbol.Letter("A"), result)
    }
}

private class FakeDialogsManager : DialogsManager {

    var alertMessage: String? = null

    override fun alert(message: String) {
        alertMessage = message
    }

    override fun confirmation(
        message: String,
        buttonText: String,
        onConfirm: () -> Unit,
    ) = Unit
}
