package me.timeto.shared

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import me.timeto.shared.db.IntervalDb
import me.timeto.shared.db.KvDb
import me.timeto.shared.db.KvDb.Companion.asAlarmSnoozeIntervalId
import me.timeto.shared.db.KvDb.Companion.asAlarmSnoozeUntil
import me.timeto.shared.db.KvDb.Companion.asTimerExpiredRepeatSeconds
import me.timeto.shared.db.KvDb.Companion.isAlarmModeDefaultEnabled

data class NotificationAlarm(
    val title: String,
    val text: String,
    val inSeconds: Int,
    val type: Type,
    val liveActivity: LiveActivity,
) {

    companion object {

        const val NO_ACTIVITY_DAYS_LIMIT = 7

        const val NOTIFICATION_ID_BREAK = 1
        const val NOTIFICATION_ID_OVERDUE = 2
        const val NOTIFICATION_ID_LIVE_UPDATE = 3
        const val NOTIFICATION_ID_NO_ACTIVITY_START = 100
        val NOTIFICATION_ID_NO_ACTIVITY_RANGE: IntRange =
            NOTIFICATION_ID_NO_ACTIVITY_START..(NOTIFICATION_ID_NO_ACTIVITY_START + NO_ACTIVITY_DAYS_LIMIT)

        const val EXPIRED_REPEAT_MAX_K = 48
        const val EXPIRED_REPEAT_HORIZON_SECONDS = 86_400
        const val EXPIRED_REPEAT_REQUEST_CODE_START = 200
        const val EXPIRED_REPEAT_NOTIFICATION_ID = 4

        const val REQUEST_CODE_ALARM = 8
        const val NOTIFICATION_ID_ALARM = 5

        fun requestCodeFor(type: Type): Int = when (type) {
            Type.TimeToBreak -> NOTIFICATION_ID_BREAK
            Type.Overdue -> NOTIFICATION_ID_OVERDUE
            is Type.NoActivity -> NOTIFICATION_ID_NO_ACTIVITY_START + type.day
            is Type.ExpiredRepeat -> EXPIRED_REPEAT_REQUEST_CODE_START + type.k
            is Type.Alarm -> REQUEST_CODE_ALARM
        }

        fun notificationIdForRequestCode(requestCode: Int): Int = when {
            requestCode == REQUEST_CODE_ALARM -> NOTIFICATION_ID_ALARM
            requestCode in EXPIRED_REPEAT_REQUEST_CODE_START..(EXPIRED_REPEAT_REQUEST_CODE_START + EXPIRED_REPEAT_MAX_K) ->
                EXPIRED_REPEAT_NOTIFICATION_ID
            else -> requestCode
        }

        /**
         * Not StateFlow to reschedule same data object.
         *
         * Each emission is a complete snapshot of what should be scheduled, so a
         * consumer only ever needs the newest one. Buffering with drop-oldest
         * keeps a slow or wedged consumer from suspending the producer, which
         * reschedules on every activity write.
         */
        val flow = MutableSharedFlow<List<NotificationAlarm>>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

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
        data class Alarm(val intervalId: Int) : Type()
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
    val lastIntervalDb = IntervalDb.selectLastOneOrNull() ?: run {
        // No interval means nothing to schedule; clear any stale alarms.
        NotificationAlarm.flow.emit(emptyList())
        return
    }

    val liveActivity = LiveActivity(lastIntervalDb)
    LiveActivity.flow.emit(liveActivity)

    val notifications = mutableListOf<NotificationAlarm>()

    val now: Int = time()
    val timerType = lastIntervalDb.buildTimerType()

    /**
     * Alarm mode replaces the one-shot break push and the repeat nag for the
     * interval that owns them, so the user gets one ongoing alarm instead of a
     * stream of notifications. Android only.
     */
    val alarmTimerType: IntervalDb.TimerType.Timer? = run {
        if (!SystemInfo.instance.isAndroid)
            return@run null
        val isAlarmMode: Boolean = lastIntervalDb.selectActivityDb().alarmModeResolved(
            globalDefault = KvDb.KEY.ALARM_MODE_DEFAULT.selectOrNull().isAlarmModeDefaultEnabled()
        )
        if (!isAlarmMode) null else timerType as? IntervalDb.TimerType.Timer
    }

    if (alarmTimerType != null) {
        /**
         * A snooze deadline survives reschedules, but only while it still belongs
         * to the running interval and that interval is still expired. Otherwise a
         * stale deadline could ring a timer that has since been restarted.
         */
        val snoozeUntil: Int = KvDb.KEY.ALARM_SNOOZE_UNTIL.selectOrNull().asAlarmSnoozeUntil()
        val isSnoozeActive: Boolean =
            KvDb.KEY.ALARM_SNOOZE_INTERVAL_ID.selectOrNull().asAlarmSnoozeIntervalId() == lastIntervalDb.id &&
                    snoozeUntil > now &&
                    alarmTimerType.isFinished(now)

        notifications.add(
            NotificationAlarm(
                title = "Time Is Over ⏰",
                text = alarmTimerType.buildExpiredString(),
                inSeconds = if (isSnoozeActive) maxOf(0, snoozeUntil - now)
                else maxOf(0, alarmTimerType.finishTime - now),
                type = NotificationAlarm.Type.Alarm(intervalId = lastIntervalDb.id),
                liveActivity = liveActivity,
            ),
        )
    } else {

        if (timerType is IntervalDb.TimerType.Timer) {
            val inSeconds: Int = timerType.finishTime - now
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
                now = now,
                liveActivity = liveActivity,
            )
        )
    }

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
