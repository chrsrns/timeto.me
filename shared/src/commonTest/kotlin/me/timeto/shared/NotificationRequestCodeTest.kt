package me.timeto.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun requestCodeFor_alarm_isReservedEight() {
        assertEquals(8, NotificationAlarm.requestCodeFor(NotificationAlarm.Type.Alarm(intervalId = 1)))
        assertEquals(NotificationAlarm.REQUEST_CODE_ALARM, NotificationAlarm.requestCodeFor(NotificationAlarm.Type.Alarm(intervalId = 42)))
    }

    @Test
    fun alarmRequestCode_collidesWithNothing() {
        val requestCode = NotificationAlarm.REQUEST_CODE_ALARM
        assertTrue(requestCode !in 1..4, "clashes with break/overdue/live ids")
        assertTrue(requestCode !in NotificationAlarm.NOTIFICATION_ID_NO_ACTIVITY_RANGE, "clashes with no-activity ids")
        assertTrue(
            requestCode !in NotificationAlarm.EXPIRED_REPEAT_REQUEST_CODE_START..(NotificationAlarm.EXPIRED_REPEAT_REQUEST_CODE_START + NotificationAlarm.EXPIRED_REPEAT_MAX_K),
            "clashes with expired-repeat ids",
        )
    }

    @Test
    fun requestCodeFor_expiredRepeat_is200PlusK() {
        assertEquals(205, NotificationAlarm.requestCodeFor(NotificationAlarm.Type.ExpiredRepeat(k = 5)))
        assertEquals(248, NotificationAlarm.requestCodeFor(NotificationAlarm.Type.ExpiredRepeat(k = 48)))
    }
}
