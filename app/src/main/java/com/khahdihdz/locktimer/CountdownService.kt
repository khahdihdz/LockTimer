package com.khahdihdz.locktimer

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * PRIMARY path: foreground service giữ đếm ngược và **tự khóa đúng thời điểm
 * kết thúc** (postDelayed + kiểm tra mỗi 10s). Alarm (AlarmScheduler) chỉ là
 * cơ chế dự phòng khi cả service bị hệ thống thu hồi.
 */
class CountdownService : Service() {

    companion object {
        const val ACTION_START = "com.khahdihdz.locktimer.action.START"
        const val ACTION_CANCEL = "com.khahdihdz.locktimer.action.CANCEL"
        const val EXTRA_DURATION_MILLIS = "duration_millis"
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 10_000L
    }

    private val handler = Handler(Looper.getMainLooper())

    /** Chạy đúng thời điểm kết thúc đang được hẹn: khóa máy. */
    private val lockRunnable = Runnable {
        if (CountdownFinisher.finishIfDue(this)) {
            Log.i(CountdownFinisher.TAG, "lockRunnable: locked at scheduled time")
        }
    }

    private val notificationUpdater = object : Runnable {
        override fun run() {
            // An toàn: nếu lockRunnable bị trễ/mất thì ticker này vẫn chốt
            if (CountdownFinisher.finishIfDue(this@CountdownService)) return
            val remaining =
                TimerRepository.endTimeRtc(this@CountdownService) - System.currentTimeMillis()
            if (remaining <= 0L) return
            updateNotification(formatRemaining(remaining))
            handler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(CountdownFinisher.TAG, "onStartCommand action=${intent?.action ?: "redelivered"}")
        when (intent?.action) {
            ACTION_CANCEL -> {
                handler.removeCallbacksAndMessages(null)
                NotificationManagerCompat.from(this).cancel(Notifier.ID_TIMER)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (handleStart()) {
                    return START_REDELIVER_INTENT
                }
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
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** @return true nếu có phiên hẹn giờ đang chạy, false nếu không hợp lệ. */
    private fun handleStart(): Boolean {
        val endTime = TimerRepository.endTimeRtc(this)
        if (endTime <= System.currentTimeMillis()) {
            // Nếu service được khởi động lại sau deadline, vẫn chạy logic kết thúc.
            CountdownFinisher.finishIfDue(this)
            return false
        }

        Log.i(
            CountdownFinisher.TAG,
            "handleStart end=$endTime now=${System.currentTimeMillis()} minus=${endTime - System.currentTimeMillis()}"
        )
        AlarmScheduler.scheduleLockAlarm(this, endTime)
        startInForeground(formatRemaining(endTime - System.currentTimeMillis()))

        handler.removeCallbacksAndMessages(null)
        // Primary: khóa đúng giây kết thúc trong tiến trình của service
        handler.postDelayed(lockRunnable, endTime - System.currentTimeMillis())
        // Fallback: kiểm tra phụ thêm mỗi 10s (nếu postDelayed bị trễ)
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
