package me.timeto.shared

import me.timeto.shared.db.EventTemplateDb
import me.timeto.shared.vm.events.templates.EventTemplateUi
import me.timeto.shared.vm.events.templates.toTemplatesUi
import kotlin.test.Test
import kotlin.test.assertEquals

class EventTemplateUiTest {

    private fun template(id: Int, text: String = "t$id") =
        EventTemplateDb(id = id, sort = 0, daytime = 0, text = text)

    @Test
    fun toTemplatesUi_reversed_newestFirst() {
        // selectAscSorted is (sort, id) ASC -> all sort=0 -> id ASC;
        // toTemplatesUi reverses -> newest (highest id) first.
        val templates = listOf(template(1), template(2), template(3))
        assertEquals(
            listOf(3, 2, 1),
            templates.toTemplatesUi().map { it.eventTemplateDb.id },
        )
    }

    @Test
    fun shortText_truncation() {
        val short = "x".repeat(17)
        assertEquals(short, EventTemplateUi(template(1, short)).shortText)

        val long = "y".repeat(18)
        assertEquals(
            "y".repeat(15) + "..",
            EventTemplateUi(template(2, long)).shortText,
        )
    }
}
