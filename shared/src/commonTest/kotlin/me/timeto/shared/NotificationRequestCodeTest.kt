package me.timeto.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationRequestCodeTest {

    @Test
    fun requestCodeFor_fixedTypes() {
        assertEquals(1, NotificationAlarm.requestCodeFor(NotificationAlarm.Type.TimeToBreak))
        assertEquals(2, NotificationAlarm.requestCodeFor(NotificationAlarm.Type.Overdue))
    }

    @Test
    fun requestCodeFor_noActivity_is100PlusDay() {
        assertEquals(101, NotificationAlarm.requestCodeFor(NotificationAlarm.Type.NoActivity(day = 1)))
        assertEquals(107, NotificationAlarm.requestCodeFor(NotificationAlarm.Type.NoActivity(day = 7)))
        (1..NotificationAlarm.NO_ACTIVITY_DAYS_LIMIT).forEach { day ->
            assertEquals(
                100 + day,
                NotificationAlarm.requestCodeFor(NotificationAlarm.Type.NoActivity(day)),
            )
        }
    }

    @Test
    fun requestCodeFor_expiredRepeat_is200PlusK() {
        assertEquals(205, NotificationAlarm.requestCodeFor(NotificationAlarm.Type.ExpiredRepeat(k = 5)))
        assertEquals(248, NotificationAlarm.requestCodeFor(NotificationAlarm.Type.ExpiredRepeat(k = 48)))
    }
}
