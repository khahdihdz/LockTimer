package com.khahdihdz.locktimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Sau khi máy khởi động lại, nếu phiên hẹn giờ vẫn còn hiệu lực thì tái lập alarm
 * và foreground service để đếm ngược tiếp tục đúng thời điểm kết thúc đã lưu.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!TimerRepository.isRunning(context)) return

        val endTime = TimerRepository.endTimeRtc(context)
        val remaining = endTime - System.currentTimeMillis()
        if (remaining <= 0L) {
            TimerRepository.stop(context)
            return
        }

        AlarmScheduler.scheduleLockAlarm(context, endTime)
        val serviceIntent = Intent(context, CountdownService::class.java)
            .setAction(CountdownService.ACTION_START)
            .putExtra(CountdownService.EXTRA_DURATION_MILLIS, remaining)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
