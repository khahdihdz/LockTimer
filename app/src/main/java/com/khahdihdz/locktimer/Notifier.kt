package com.khahdihdz.locktimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Các notification của app: đếm ngược (foreground service, silent) và thông báo
 * "đã khóa máy" khi hết giờ.
 */
object Notifier {

    const val CHANNEL_TIMER = "lock_timer_channel"
    const val CHANNEL_LOCKED = "lock_timer_locked"
    const val ID_TIMER = 1001
    const val ID_LOCKED = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TIMER,
                context.getString(R.string.notif_channel_timer),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_timer_desc)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOCKED,
                context.getString(R.string.notif_channel_locked),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_locked_desc)
            }
        )
    }

    fun timerNotification(context: Context, remainingText: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.notif_timer_title))
            .setContentText(remainingText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .build()
    }

    fun lockedNotification(context: Context, lockSucceeded: Boolean = true): Notification =
        NotificationCompat.Builder(context, CHANNEL_LOCKED)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(
                if (lockSucceeded) context.getString(R.string.notif_locked_title)
                else context.getString(R.string.notif_lock_failed_title)
            )
            .setContentText(context.getString(R.string.notif_locked_text))
            .setAutoCancel(true)
            .build()
}
