package com.khahdihdz.locktimer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val update = UpdateChecker.fetchLatest() ?: return Result.success()
            showNotification(update)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(update: UpdateInfo) {
        val preferences = applicationContext.getSharedPreferences("release_updates", Context.MODE_PRIVATE)
        if (preferences.getString("last_notified_version", null) == update.versionName) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Cập nhật LockTimer",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val openApp = PendingIntent.getActivity(
            applicationContext,
            7102,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("LockTimer có bản cập nhật ${update.versionName}")
            .setContentText("Mở ứng dụng để tải và cài đặt phiên bản mới.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Đã có LockTimer ${update.versionName}. Mở ứng dụng để cập nhật.")
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            preferences.edit().putString("last_notified_version", update.versionName).apply()
        }
    }

    companion object {
        private const val CHANNEL_ID = "locktimer_updates"
        private const val NOTIFICATION_ID = 7102
    }
}

object UpdateWorkScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            12, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "locktimer-release-check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
