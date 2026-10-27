package com.khahdihdz.locktimer

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat

/**
 * Foreground service giữ đếm ngược + notification thời gian còn lại.
 * Thời điểm khóa thực tế được đảm bảo bởi AlarmScheduler (setExactAndAllowWhileIdle).
 */
class CountdownService : Service() {

    companion object {
        const val ACTION_START = "com.khahdihdz.locktimer.action.START"
        const val ACTION_CANCEL = "com.khahdihdz.locktimer.action.CANCEL"
        const val EXTRA_DURATION_MILLIS = "duration_millis"
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 10_000L
    }

    private val handler = Handler(Looper.getMainLooper())

    private val notificationUpdater = object : Runnable {
        override fun run() {
            val remaining =
                TimerRepository.endTimeRtc(this@CountdownService) - System.currentTimeMillis()
            if (remaining <= 0L) return
            updateNotification(formatRemaining(remaining))
            handler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                handler.removeCallbacks(notificationUpdater)
                NotificationManagerCompat.from(this).cancel(Notifier.ID_TIMER)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (handleStart()) return START_REDELIVER_INTENT
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(notificationUpdater)
        super.onDestroy()
    }

    /** @return true nếu có phiên hẹn giờ đang chạy, false nếu không hợp lệ. */
    private fun handleStart(): Boolean {
        val endTime = TimerRepository.endTimeRtc(this)
        if (endTime <= System.currentTimeMillis()) return false

        AlarmScheduler.scheduleLockAlarm(this, endTime)
        startInForeground(formatRemaining(endTime - System.currentTimeMillis()))
        handler.removeCallbacks(notificationUpdater)
        handler.postDelayed(notificationUpdater, NOTIFICATION_UPDATE_INTERVAL_MS)
        return true
    }

    private fun startInForeground(remainingText: String) {
        Notifier.ensureChannels(this)
        val notification = Notifier.timerNotification(this, remainingText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notifier.ID_TIMER,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Notifier.ID_TIMER, notification)
        }
    }

    private fun updateNotification(remainingText: String) {
        val notification = Notifier.timerNotification(this, remainingText)
        runCatching {
            NotificationManagerCompat.from(this).notify(Notifier.ID_TIMER, notification)
        }
    }
}
