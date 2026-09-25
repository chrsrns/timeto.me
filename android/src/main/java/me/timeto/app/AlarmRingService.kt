package me.timeto.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import me.timeto.shared.NotificationAlarm
import me.timeto.shared.getSoundTimerExpiredFileName
import me.timeto.shared.reportApi

/**
 * Owns the ongoing alarm: a looping alarm-stream player plus vibration, kept
 * alive by a mediaPlayback foreground service.
 *
 * Notification channels cannot loop, so the ring cannot be a channel sound.
 *
 * Armed directly by [AlarmCenter] through `PendingIntent.getForegroundService`
 * rather than a receiver that starts the service, because the process can be
 * killed between the two.
 */
class AlarmRingService : Service() {

    companion object {

        const val ACTION_START = "me.timeto.app.action.ALARM_RING_START"
        const val ACTION_SNOOZE = "me.timeto.app.action.ALARM_RING_SNOOZE"
        const val ACTION_STOP = "me.timeto.app.action.ALARM_RING_STOP"

        const val EXTRA_INTERVAL_ID = "alarm_ring_interval_id"

        /** Visible for tests and for [AlarmCenter]'s cancel path. */
        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var ringingIntervalId: Int? = null
            private set

        /** Test seam: playback is prepared once per ring, never per reschedule. */
        @Volatile
        internal var prepareCount: Int = 0
            private set

        fun buildIntent(
            context: Context,
            action: String,
            intervalId: Int? = null,
        ): Intent = Intent(context, AlarmRingService::class.java).apply {
            this.action = action
            if (intervalId != null)
                putExtra(EXTRA_INTERVAL_ID, intervalId)
        }

        fun start(context: Context, intervalId: Int) {
            context.startForegroundService(buildIntent(context, ACTION_START, intervalId))
        }

        fun stop(context: Context) {
            context.stopService(buildIntent(context, ACTION_STOP))
        }
    }

    ///

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val intervalId: Int = intent?.getIntExtra(EXTRA_INTERVAL_ID, 0) ?: 0

        // V271: a start while already ringing is a no-op, so a reschedule cannot
        // restart the audio under the user.
        if (isRunning && ringingIntervalId == intervalId)
            return START_NOT_STICKY

        startForeground()

        if (isRunning)
            releasePlayback()

        ringingIntervalId = intervalId
        isRunning = true

        startPlayback()
        startVibration()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releasePlayback()
        isRunning = false
        ringingIntervalId = null
        super.onDestroy()
    }

    ///

    private fun startForeground() {
        val notification: Notification = buildRingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NotificationAlarm.NOTIFICATION_ID_ALARM,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NotificationAlarm.NOTIFICATION_ID_ALARM, notification)
        }
    }

    private fun buildRingNotification(): Notification {
        val channel = NotificationsUtils.channelTimerExpired()
        val pIntent = PendingIntent.getActivity(
            this,
            NotificationAlarm.REQUEST_CODE_ALARM,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channel.id)
            .setSmallIcon(R.drawable.readme_notification_alarm)
            .setColor(0x0055FF)
            .setContentTitle("Time Is Over ⏰")
            .setContentText("Snooze or start a new activity")
            .setOngoing(true)
            .setContentIntent(pIntent)
            .build()
    }

    private fun startPlayback() {
        try {
            val soundName: String = getSoundTimerExpiredFileName(withExtension = true)
            val player = MediaPlayer()
            player.setDataSource(
                this,
                Uri.parse("android.resource://${packageName}/raw/$soundName"),
            )
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.isLooping = true
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
            mediaPlayer = player
            prepareCount += 1
        } catch (e: Throwable) {
            reportApi("AlarmRingService.startPlayback():$e")
        }
    }

    private fun startVibration() {
        try {
            val manager: VibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val v: Vibrator = manager.defaultVibrator
            v.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 700, 700),
                    0,
                )
            )
            vibrator = v
        } catch (e: Throwable) {
            reportApi("AlarmRingService.startVibration():$e")
        }
    }

    private fun releasePlayback() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying)
                    player.stop()
            } catch (_: Throwable) {
            }
            player.release()
        }
        mediaPlayer = null

        vibrator?.let { v ->
            try {
                v.cancel()
            } catch (_: Throwable) {
            }
        }
        vibrator = null
    }
}
