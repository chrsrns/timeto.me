package me.timeto.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationIdMappingTest {

    @Test
    fun expiredRepeatRange_mapsToSingleId() {
        (200..248).forEach { requestCode ->
            assertEquals(
                NotificationAlarm.EXPIRED_REPEAT_NOTIFICATION_ID,
                NotificationAlarm.notificationIdForRequestCode(requestCode),
                "requestCode=$requestCode should map to the shared expired-repeat id",
            )
        }
    }

    @Test
    fun outsideRange_isIdentity() {
        // Existing request codes post under their own id
        listOf(0, 1, 2, 3, 100, 101, 107, 199, 249, 300, -5).forEach { requestCode ->
            assertEquals(requestCode, NotificationAlarm.notificationIdForRequestCode(requestCode))
        }
    }

    @Test
    fun kToRequestCode_staysInRange() {
        (1..NotificationAlarm.EXPIRED_REPEAT_MAX_K).forEach { k ->
            val requestCode = NotificationAlarm.EXPIRED_REPEAT_REQUEST_CODE_START + k
            assertTrue(requestCode in 200..248, "k=$k produced $requestCode outside 200..248")
        }
    }

    @Test
    fun constants_matchSpec() {
        assertEquals(48, NotificationAlarm.EXPIRED_REPEAT_MAX_K)
        assertEquals(200, NotificationAlarm.EXPIRED_REPEAT_REQUEST_CODE_START)
        assertEquals(4, NotificationAlarm.EXPIRED_REPEAT_NOTIFICATION_ID)
        assertEquals(86_400, NotificationAlarm.EXPIRED_REPEAT_HORIZON_SECONDS)
    }
}
