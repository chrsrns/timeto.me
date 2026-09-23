package me.timeto.app

import me.timeto.app.widget.MyWidgetOpenApp.AppAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// V177: valid strings map to actions; invalid input → reportApi + null.
// reportApi on invalid input runs in a background scope — harmless noise
// without an initialized db.
class MyWidgetOpenAppTest {

    @Test
    fun parse_validActions() {
        assertTrue(AppAction.parse("new-task") is AppAction.NewTask)
        assertTrue(AppAction.parse("open-calendar") is AppAction.OpenCalendar)
        assertEquals(
            AppAction.OpenTaskFolder(5),
            AppAction.parse("open-task-folder:5"),
        )
        assertEquals(
            AppAction.OpenNoteFolder(9),
            AppAction.parse("open-note-folder:9"),
        )
    }

    @Test
    fun parse_rawRoundTrip() {
        val action = AppAction.OpenTaskFolder(42)
        assertEquals(action, AppAction.parse(action.raw))
        assertEquals(AppAction.NewTask, AppAction.parse(AppAction.NewTask.raw))
    }

    @Test
    fun parse_invalid_returnsNull() {
        assertNull(AppAction.parse("bogus"))
        assertNull(AppAction.parse(""))
        assertNull(AppAction.parse("open-task-folder:x"))
        assertNull(AppAction.parse("open-note-folder:"))
    }
}
