package me.timeto.shared

import me.timeto.shared.db.validateTextEx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RepeatingValidateTextTest {

    @Test
    fun validateTextEx_trims() {
        assertEquals("jog", validateTextEx("  jog  "))
    }

    @Test
    fun validateTextEx_blank_throwsEmptyText() {
        listOf("", "   ").forEach { input ->
            val e = assertFailsWith<UiException>("input='$input'") {
                validateTextEx(input)
            }
            assertEquals("Empty text", e.uiMessage)
        }
    }
}
