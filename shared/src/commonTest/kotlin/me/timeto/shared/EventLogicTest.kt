package me.timeto.shared

import me.timeto.shared.db.EventDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventLogicTest {

    @Test
    fun prepTextForTask_emitsEventToken() {
        val event = EventDb(
            id = 1,
            text = "meet",
            utc_time = 1_750_000_000 + localUtcOffset,
        )
        val text = event.prepTextForTask()
        assertEquals("meet #e1750000000", text)

        val features = text.textFeatures()
        assertEquals(1_750_000_000, features.fromEvent!!.unixTime.time)
        assertEquals("meet", features.textNoFeatures)
    }

    @Test
    fun prepTextForTask_preservesExistingFeatures() {
        val event = EventDb(
            id = 1,
            text = "meet #t3600",
            utc_time = 1_750_000_000 + localUtcOffset,
        )
        val features = event.prepTextForTask().textFeatures()
        assertEquals(
            TextFeatures.TimerType.Timer(3_600),
            features.timerType,
        )
        assertEquals(1_750_000_000, features.fromEvent!!.unixTime.time)
        assertEquals("meet", features.textNoFeatures)
    }

    @Test
    fun getLocalTime_subtractsUtcOffset() {
        val event = EventDb(
            id = 1,
            text = "meet",
            utc_time = 1_750_000_000,
        )
        assertEquals(1_750_000_000 - localUtcOffset, event.getLocalTime().time)
    }

    @Test
    fun plainText_hasNoEvent() {
        val features = "meet".textFeatures()
        assertNull(features.fromEvent)
    }
}
