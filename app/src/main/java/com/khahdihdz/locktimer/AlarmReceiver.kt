package com.khahdihdz.locktimer

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * Bắn khi alarm hẹn giờ đổ chuông: khóa màn hình (lockNow) nếu có quyền Device Admin,
 * dọn trạng thái + notification, stop service.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_LOCK = "com.khahdihdz.locktimer.action.LOCK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOCK) return

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, LockDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            runCatching { dpm.lockNow() }
        }

        TimerRepository.stop(context)
        AlarmScheduler.cancelLockAlarm(context)
        Notifier.ensureChannels(context)

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(Notifier.ID_TIMER)
        runCatching {
            notificationManager.notify(Notifier.ID_LOCKED, Notifier.lockedNotification(context))
        }

        runCatching { context.stopService(Intent(context, CountdownService::class.java)) }
    }
}
