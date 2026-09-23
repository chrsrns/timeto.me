package me.timeto.shared

import me.timeto.shared.vm.emoji.EmojiPickerVm
import kotlin.test.Test
import kotlin.test.assertEquals

// V197: lowercase + trim; empty restores all; whitespace-separated
// words must ALL be contained in the emoji tags.
// Note: the init's getResourceContent("emojis","json") throws on JVM
// (androidApplication uninitialized) inside a caught background scope —
// harmless noise; allEmojis is seeded directly instead.
class EmojiPickerVmTest {

    private fun vm(): EmojiPickerVm =
        EmojiPickerVm().apply {
            allEmojis = listOf(
                EmojiPickerVm.Emoji(emoji = "A", tags = "smile happy face"),
                EmojiPickerVm.Emoji(emoji = "B", tags = "sad tear face"),
                EmojiPickerVm.Emoji(emoji = "C", tags = "cat animal"),
            )
        }

    @Test
    fun emptySearch_restoresAll() {
        val vm = vm()
        vm.search("sad")
        assertEquals(listOf("B"), vm.state.value.emojis.map { it.emoji })
        vm.search("   ")
        assertEquals(listOf("A", "B", "C"), vm.state.value.emojis.map { it.emoji })
    }

    @Test
    fun multiWord_allMustMatch() {
        val vm = vm()
        vm.search("sad face")
        assertEquals(listOf("B"), vm.state.value.emojis.map { it.emoji })
        vm.search("face happy")
        assertEquals(listOf("A"), vm.state.value.emojis.map { it.emoji })
    }

    @Test
    fun normalizes_caseAndWhitespace() {
        val vm = vm()
        vm.search("  FACE   CAT ")
        assertEquals(emptyList(), vm.state.value.emojis.map { it.emoji })
        vm.search("FACE")
        assertEquals(listOf("A", "B"), vm.state.value.emojis.map { it.emoji })
    }
}
