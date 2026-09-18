package me.timeto.shared

import kotlinx.coroutines.flow.MutableSharedFlow
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.KvDb.Companion.asTimerExpiredRepeatSeconds

data class NotificationAlarm(
    val title: String,
    val text: String,
    val inSeconds: Int,
    val type: Type,
    val liveActivity: LiveActivity,
) {

    companion object {

        const val NO_ACTIVITY_DAYS_LIMIT = 7

        const val EXPIRED_REPEAT_MAX_K = 48
        const val EXPIRED_REPEAT_HORIZON_SECONDS = 86_400
        const val EXPIRED_REPEAT_REQUEST_CODE_START = 200
        const val EXPIRED_REPEAT_NOTIFICATION_ID = 4

        fun notificationIdForRequestCode(requestCode: Int): Int =
            if (requestCode in EXPIRED_REPEAT_REQUEST_CODE_START..(EXPIRED_REPEAT_REQUEST_CODE_START + EXPIRED_REPEAT_MAX_K))
                EXPIRED_REPEAT_NOTIFICATION_ID
            else requestCode

        // Not StateFlow to reschedule same data object
        val flow = MutableSharedFlow<List<NotificationAlarm>>()

        suspend fun rescheduleAll() {
            rescheduleNotifications()
        }
    }

    ///

    sealed class Type {
        object TimeToBreak : Type()
        object Overdue : Type()
        data class NoActivity(val day: Int) : Type()
        data class ExpiredRepeat(val k: Int) : Type()
    }
}

fun buildExpiredRepeatNotifications(
    timerType: IntervalDb.TimerType,
    repeatSeconds: Int,
    now: Int,
    liveActivity: LiveActivity,
): List<NotificationAlarm> {

    if (repeatSeconds <= 0)
        return emptyList()

    val baseDelay: Int = when (timerType) {
        is IntervalDb.TimerType.Timer ->
            maxOf(0, timerType.finishTime - now)
        is IntervalDb.TimerType.OverdueTimer -> 0
        is IntervalDb.TimerType.Stopwatch ->
            return emptyList()
    }

    return (1..NotificationAlarm.EXPIRED_REPEAT_MAX_K)
        .filter { (it * repeatSeconds) <= NotificationAlarm.EXPIRED_REPEAT_HORIZON_SECONDS }
        .map { k -> k to (baseDelay + (k * repeatSeconds)) }
        .filter { (_, inSeconds) -> inSeconds > 0 }
        .map { (k, inSeconds) ->
            NotificationAlarm(
                title = "Time Is Over ⏰",
                text = "Overdue by ${(k * repeatSeconds).toTimerHintNote(isShort = false)}",
                inSeconds = inSeconds,
                type = NotificationAlarm.Type.ExpiredRepeat(k = k),
                liveActivity = liveActivity,
            )
        }
}

private suspend fun rescheduleNotifications() {
    val lastIntervalDb = IntervalDb.selectLastOneOrNull()!!

    val liveActivity = LiveActivity(lastIntervalDb)
    LiveActivity.flow.emit(liveActivity)

    val notifications = mutableListOf<NotificationAlarm>()

    val timerType = lastIntervalDb.buildTimerType()
    if (timerType is IntervalDb.TimerType.Timer) {
        val inSeconds: Int = timerType.finishTime - time()
        if (inSeconds > 0) {
            notifications.add(
                NotificationAlarm(
                    title = "Time Is Over ⏰",
                    text = timerType.buildExpiredString(),
                    inSeconds = inSeconds,
                    type = NotificationAlarm.Type.TimeToBreak,
                    liveActivity = liveActivity,
                ),
            )
        }
    }

    val expiredRepeatSeconds: Int =
        KvDb.KEY.TIMER_EXPIRED_REPEAT_SECONDS.selectOrNull().asTimerExpiredRepeatSeconds()
    notifications.addAll(
        buildExpiredRepeatNotifications(
            timerType = timerType,
            repeatSeconds = expiredRepeatSeconds,
            now = time(),
            liveActivity = liveActivity,
        )
    )

    val oneDaySeconds = 86_400
    (1..NotificationAlarm.NO_ACTIVITY_DAYS_LIMIT).forEach { day ->
        val notificationTime: Int =
            lastIntervalDb.time + (day * oneDaySeconds)
        val inSeconds: Int =
            notificationTime - time()
        if (inSeconds <= 0)
            return@forEach
        notifications.add(
            NotificationAlarm(
                title = "No activity for $day day${if (day > 1) "s" else ""}",
                text = "It's okay. Just back to goals.",
                inSeconds = inSeconds,
                type = NotificationAlarm.Type.NoActivity(day = day),
                liveActivity = liveActivity,
            ),
        )
    }

    NotificationAlarm.flow.emit(notifications)

    /*
    val activityDb = lastInterval.getActivity()
    val pomodoroTimer = activityDb.pomodoro_timer
    if (pomodoroTimer > 0) {
        scheduledNotificationsDataFlow.emit(
            listOf(
                ScheduledNotificationData(
                    title = "Time to Break  ✅",
                    text = if (totalMinutes == 1) "1 minute has expired" else "$totalMinutes minutes have expired",
                    inSeconds = inSeconds,
                    type = ScheduledNotificationData.TYPE.BREAK,
                ),
                ScheduledNotificationData(
                    title = "Break Is Over ⏰",
                    text = "Restart or set the timer",
                    inSeconds = inSeconds + pomodoroTimer,
                    type = ScheduledNotificationData.TYPE.OVERDUE,
                ),
            )
        )
    } else {
        scheduledNotificationsDataFlow.emit(
            listOf(
                ScheduledNotificationData(
                    title = "Time Is Over ⏰",
                    text = if (totalMinutes == 1) "1 minute has expired" else "$totalMinutes minutes have expired",
                    inSeconds = inSeconds,
                    type = ScheduledNotificationData.TYPE.OVERDUE,
                ),
            )
        )
    }
    */
}
