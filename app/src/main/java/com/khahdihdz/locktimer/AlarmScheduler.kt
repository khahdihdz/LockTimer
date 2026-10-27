package com.khahdihdz.locktimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Lên lịch alarm "dự phòng" tại đúng thời điểm kết thúc. Dù foreground service bị
 * hệ thống thu hồi, alarm setExactAndAllowWhileIdle vẫn bắn AlarmReceiver để khóa máy.
 */
object AlarmScheduler {

    private const val REQUEST_CODE_LOCK = 2001

    fun scheduleLockAlarm(context: Context, triggerAtRtcMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = lockPendingIntent(context)
        val canScheduleExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtRtcMillis, pendingIntent
            )
        } else {
            // fallback: alarm không chính xác tuyệt đối nhưng vẫn bắn trong khoảng 60s
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAtRtcMillis, 60_000L, pendingIntent)
        }
    }

    fun cancelLockAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(lockPendingIntent(context))
    }

    private fun lockPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_LOCK,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_LOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
