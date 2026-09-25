package me.timeto.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import me.timeto.app.NotificationsUtils.NOTIFICATION_ID_BREAK
import me.timeto.app.NotificationsUtils.NOTIFICATION_ID_OVERDUE
import me.timeto.shared.NotificationAlarm
import me.timeto.shared.timeMls

object AlarmCenter {

    /**
     * The interval the ring was last armed for. Used as the ringing-interval
     * identity when the service is not running yet, so the cancel path never
     * has to consult the latest interval, which a new interval would have
     * replaced.
     */
    private var armedAlarmIntervalId: Int? = null

    fun scheduleNotification(data: NotificationAlarm) {
        val type = data.type
        if (type is NotificationAlarm.Type.Alarm) {
            scheduleAlarmRing(
                intervalId = type.intervalId,
                inSeconds = data.inSeconds,
            )
            return
        }

        val requestCode: Int = NotificationAlarm.requestCodeFor(type)

        val context = App.instance
        val intent = Intent(context, TimerNotificationReceiver::class.java)

        intent.putExtra(TimerNotificationReceiver.EXTRA_TITLE, data.title)
        intent.putExtra(TimerNotificationReceiver.EXTRA_TEXT, data.text)
        intent.putExtra(TimerNotificationReceiver.EXTRA_REQUEST_CODE, requestCode)

        TimerNotificationReceiver.liveDataEncode(intent, data.liveActivity)

        val pIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /**
         * setExactAndAllowWhileIdle(), can be delayed for 10 minutes.
         *
         * Based on https://medium.com/@igordias/75c409f3bde0 use setAlarmClock().
         * Works better. I do not know why to use 2 times pIntent, but it's okay.
         */
        val alarm = getAlarmManager()
        val alarmInfo = AlarmManager.AlarmClockInfo(timeMls() + (data.inSeconds * 1_000L), pIntent)
        alarm.setAlarmClock(alarmInfo, pIntent)
    }

    /**
     * Arms the service itself rather than a receiver that starts it: the process
     * can be killed between the two, and an exact alarm may legally start a
     * mediaPlayback service from the background.
     */
    fun scheduleAlarmRing(
        intervalId: Int,
        inSeconds: Int,
    ) {
        // A due alarm that is already ringing has nothing to re-arm. Without this
        // every reschedule re-arms it at "now", and the system fires it again
        // immediately: a user-visible alarm churning while the ring is up.
        if (inSeconds <= 0 &&
            AlarmRingService.isRunning &&
            AlarmRingService.ringingIntervalId == intervalId
        ) return

        armedAlarmIntervalId = intervalId
        val context = App.instance
        val pIntent = buildAlarmRingPendingIntent(context)
        val alarm = getAlarmManager()
        val alarmInfo = AlarmManager.AlarmClockInfo(timeMls() + (inSeconds * 1_000L), pIntent)
        alarm.setAlarmClock(alarmInfo, pIntent)
    }

    /**
     * Always cancels the armed ring, so a stale alarm cannot fire after the
     * interval it belonged to is gone.
     */
    fun cancelAlarmRing() {
        armedAlarmIntervalId = null
        getAlarmManager().cancel(buildAlarmRingPendingIntent(App.instance))
    }

    /**
     * @param stopRingService whether this pass may stop the ring at all. The
     * ring survives when the emitted list still holds an expired alarm for the
     * interval that is ringing, so an unrelated reschedule does not silence it.
     */
    fun cancelAllAlarms(
        stopRingService: Boolean,
        notifications: List<NotificationAlarm>,
    ) {
        val context = App.instance
        val intent = Intent(context, TimerNotificationReceiver::class.java)
        val alarm = getAlarmManager()

        val requestCodes: List<Int> =
            listOf(NOTIFICATION_ID_BREAK, NOTIFICATION_ID_OVERDUE) +
                    NotificationsUtils.NOTIFICATION_ID_NO_ACTIVITY_RANGE +
                    NotificationsUtils.EXPIRED_REPEAT_REQUEST_CODE_RANGE
        requestCodes.forEach { requestCode ->
            val pIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarm.cancel(pIntent)
        }

        // The ring is armed as a foreground service on its own request code, so
        // the broadcast loop above can never match it. Without this cancel a
        // superseded ring would still fire.
        alarm.cancel(buildAlarmRingPendingIntent(context))

        if (stopRingService && !notifications.hasExpiredAlarmFor(ringingIntervalIdOrNull()))
            AlarmRingService.stop(context)
    }

    private fun ringingIntervalIdOrNull(): Int? =
        AlarmRingService.ringingIntervalId ?: armedAlarmIntervalId
}

///

private fun List<NotificationAlarm>.hasExpiredAlarmFor(intervalId: Int?): Boolean {
    if (intervalId == null)
        return false
    return any { alarm ->
        val type = alarm.type
        type is NotificationAlarm.Type.Alarm && type.intervalId == intervalId && alarm.inSeconds == 0
    }
}

private fun buildAlarmRingPendingIntent(context: Context): PendingIntent =
    PendingIntent.getForegroundService(
        context,
        NotificationAlarm.REQUEST_CODE_ALARM,
        AlarmRingService.buildIntent(context, AlarmRingService.ACTION_START),
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun getAlarmManager(): AlarmManager =
    App.instance.getSystemService(Context.ALARM_SERVICE) as AlarmManager
