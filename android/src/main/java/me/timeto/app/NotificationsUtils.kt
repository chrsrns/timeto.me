package me.timeto.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import me.timeto.shared.NotificationAlarm
import me.timeto.shared.getSoundTimerExpiredFileName

/**
 * WARNING
 * DO NOT CHANGE FILE NAME FOR SOUND FILES. Otherwise, they will stop working.
 *
 * Common docs: https://developer.android.com/guide/topics/ui/notifiers/notifications
 * Channel docs: https://developer.android.com/training/notify-user/channels
 */
object NotificationsUtils {

    const val NOTIFICATION_ID_BREAK = NotificationAlarm.NOTIFICATION_ID_BREAK
    const val NOTIFICATION_ID_OVERDUE = NotificationAlarm.NOTIFICATION_ID_OVERDUE
    const val NOTIFICATION_ID_LIVE_UPDATE = NotificationAlarm.NOTIFICATION_ID_LIVE_UPDATE
    const val NOTIFICATION_ID_EXPIRED_REPEAT = NotificationAlarm.EXPIRED_REPEAT_NOTIFICATION_ID

    // region NO_ACTIVITY
    const val NOTIFICATION_ID_NO_ACTIVITY_START = NotificationAlarm.NOTIFICATION_ID_NO_ACTIVITY_START
    val NOTIFICATION_ID_NO_ACTIVITY_RANGE: IntRange =
        NotificationAlarm.NOTIFICATION_ID_NO_ACTIVITY_RANGE
    // endregion

    // region EXPIRED_REPEAT
    val EXPIRED_REPEAT_REQUEST_CODE_RANGE: IntRange =
        NotificationAlarm.EXPIRED_REPEAT_REQUEST_CODE_START..(NotificationAlarm.EXPIRED_REPEAT_REQUEST_CODE_START + NotificationAlarm.EXPIRED_REPEAT_MAX_K)
    // endregion

    val manager: NotificationManager =
        App.instance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun channelTimerExpired(): NotificationChannel =
        upsertChannel("timer_expired", "Timer Expired", getSoundTimerExpiredFileName(false))

    fun channelTimerOverdue(): NotificationChannel =
        upsertChannel("timer_overdue", "Timer Overdue", null)

    fun channelTimerExpiredRepeat(): NotificationChannel =
        upsertChannel("timer_expired_repeat", "Timer Expired Repeat", null)

    fun channelLiveUpdates(): NotificationChannel {
        // IMPORTANCE_DEFAULT is obligatory for live updates
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("live_updates", "Live Updates", importance)
        // Disable sound for creation and each update
        channel.setSound(null, null)
        manager.createNotificationChannel(channel)
        return channel
    }

    /**
     * IMPORTANCE_HIGH is required for a full-screen intent to launch, and the
     * channel carries no sound: the ring is played by AlarmRingService, and a
     * channel sound would play over it.
     */
    fun channelAlarmRing(): NotificationChannel {
        val channel = NotificationChannel("alarm_ring", "Alarm", NotificationManager.IMPORTANCE_HIGH)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        channel.setSound(null, null)
        channel.enableVibration(false)
        manager.createNotificationChannel(channel)
        return channel
    }

    /**
     * Android 14+ turns USE_FULL_SCREEN_INTENT into a special app access that
     * Play only auto-grants to calling and alarm apps, so the ring must never
     * depend on it.
     */
    fun canUseFullScreenIntent(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            manager.canUseFullScreenIntent()
        else true

    fun buildFullScreenIntentSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            .setData(Uri.fromParts("package", App.instance.packageName, null))

    /**
     * According to documentation only first call affects. Second do nothing.
     *
     * @param soundName File name, id can be changed
     */
    fun upsertChannel(
        id: String,
        name: String,
        soundName: String?,
    ): NotificationChannel {
        /**
         * With IMPORTANCE_LOW device can show the notification only it the shade not in status bar.
         * todo https://developer.android.com/reference/android/app/NotificationManager#shouldHideSilentStatusBarIcons()
         */
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        if (soundName != null)
            channel.setSound(
                Uri.parse("android.resource://${App.instance.packageName}/raw/$soundName"),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            )
        manager.createNotificationChannel(channel)
        return channel
    }

    /**
     * At least on miui_12, if the "Badge -> Dot" is checked in the notification settings for
     * an app, when the application is opened, the notifications would removed automatically.
     */
    fun cleanTimerPushes() {
        manager.cancel(NOTIFICATION_ID_BREAK)
        manager.cancel(NOTIFICATION_ID_OVERDUE)
        manager.cancel(NOTIFICATION_ID_EXPIRED_REPEAT)
        NOTIFICATION_ID_NO_ACTIVITY_RANGE.forEach { id ->
            manager.cancel(id)
        }
    }
}
