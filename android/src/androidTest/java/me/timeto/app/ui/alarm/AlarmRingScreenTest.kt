package me.timeto.app.ui.alarm

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The alarm screen carries its own snooze so the alarm stays stoppable when
 * notifications are denied or the full-screen grant is missing, and it must
 * offer no way to dismiss the ring outright.
 */
@RunWith(AndroidJUnit4::class)
class AlarmRingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun snoozeControl_isPresentAndInvokesSnooze() {
        var snoozed = false
        composeRule.setContent {
            AlarmRingScreen(onSnooze = { snoozed = true }, onStart = {})
        }

        composeRule.onNodeWithText("Snooze").performClick()

        assertTrue(snoozed)
    }

    @Test
    fun startControl_deepLinksHome() {
        var started = false
        composeRule.setContent {
            AlarmRingScreen(onSnooze = {}, onStart = { started = true })
        }

        composeRule.onNodeWithText("Start").performClick()

        assertTrue(started)
    }

    @Test
    fun noDismissControl() {
        composeRule.setContent {
            AlarmRingScreen(onSnooze = {}, onStart = {})
        }

        listOf("Dismiss", "Stop", "Turn Off", "Silence").forEach { label ->
            composeRule.onAllNodesWithText(label, substring = true)
                .assertCountEquals(0)
        }
    }
}
