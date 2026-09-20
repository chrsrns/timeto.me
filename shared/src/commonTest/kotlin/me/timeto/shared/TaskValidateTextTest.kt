package me.timeto.shared

import me.timeto.shared.db.TaskDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskValidateTextTest {

    @Test
    fun validateText_extractsTimerHint() {
        val validated = TaskDb.validateText("call 5min")
        val features = validated.textFeatures()
        assertEquals(
            TextFeatures.TimerType.Timer(300),
            features.timerType,
        )
        assertEquals("call", features.textNoFeatures)
    }

    @Test
    fun validateText_noHint_passesThrough() {
        val features = TaskDb.validateText("call mom").textFeatures()
        assertEquals("call mom", features.textNoFeatures)
        assertEquals(null, features.timerType)
    }

    @Test
    fun validateText_blank_throws() {
        listOf("", "   ").forEach { input ->
            val e = assertFailsWith<UiException>("input='$input'") {
                TaskDb.validateText(input)
            }
            assertEquals("Empty text", e.uiMessage)
        }
    }

    @Test
    fun validateText_timerOnly_textEmpty() {
        // "5min" alone leaves no text, but the timer token keeps it non-empty
        val validated = TaskDb.validateText("5min")
        val features = validated.textFeatures()
        assertEquals(
            TextFeatures.TimerType.Timer(300),
            features.timerType,
        )
    }
}
