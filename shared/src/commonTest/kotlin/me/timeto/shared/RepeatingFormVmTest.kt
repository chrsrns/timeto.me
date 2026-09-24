package me.timeto.shared

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.timeto.shared.db.ActivityDb
import me.timeto.shared.db.RepeatingDb
import me.timeto.shared.vm.repeatings.form.RepeatingFormVm
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatingFormVmTest {

    @Test
    fun save_requiredFields_alertInOrder() = runBlocking {
        val vm = RepeatingFormVm(null)
        val dialogsManager = TestDialogsManager()

        suspend fun saveAndAwaitAlert(): String =
            withTimeout(5_000) {
                vm.save(dialogsManager) {}
                dialogsManager.alerts.receive()
            }

        assertEquals("No text", saveAndAwaitAlert())

        vm.setText("x")
        assertEquals("Period not selected", saveAndAwaitAlert())

        vm.setPeriod(RepeatingDb.Period.EveryNDays(1))
        assertEquals("Time of the day is not selected", saveAndAwaitAlert())

        vm.setDaytime(DaytimeUi(hour = 12, minute = 0))
        assertEquals("Activity not selected", saveAndAwaitAlert())

        vm.setActivity(testActivityDb())
        assertEquals("Timer not selected", saveAndAwaitAlert())
    }

    private fun testActivityDb(): ActivityDb = ActivityDb(
        id = 1, parent_id = null, type_id = 0, name = "Work",
        goal_json = null, timer = 0, period_json = """{"type":2}""",
        symbol_raw = "", home_button_sort = "", color_rgba = "52,199,89,255",
        keep_screen_on = 0, pomodoro_timer = 0, checklist_hint = 0,
        timer_hints = "",
        alarm_mode = null,
    )
}
