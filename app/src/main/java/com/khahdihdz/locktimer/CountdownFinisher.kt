package com.khahdihdz.locktimer

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * Logic "kết thúc hẹn giờ" dùng chung cho mọi đường dẫn kích hoạt:
 * - AlarmReceiver (alarm dự phòng khi service bị thu hồi)
 * - CountdownService (primary: service tự khóa đúng thời điểm kết thúc)
 *
 * Service gọi trực tiếp làm đường chính; alarm chỉ là cơ chế dự phòng khi
 * cả service bị hệ thống thu hồi (các ROM OEM thường kill app phải xin
 * auto-start riêng). Nếu màn hình không khóa, ưu tiên xem log với tag
 * "LockTimer" để xác định đường nào đã bắn.
 */
object CountdownFinisher {

    const val TAG = "LockTimer"

    /**
     * Chỉ khóa khi đã đến/qua thời điểm kết thúc. Trả về true nếu đã xử lý xong.
     * endTimeRtc == 0 nghĩa là hẹn giờ đã kết thúc/không tồn tại — bỏ qua.
     */
    fun finishIfDue(context: Context): Boolean {
        val endTimeRtc = TimerRepository.endTimeRtc(context)
        if (endTimeRtc == 0L) return false
        if (System.currentTimeMillis() < endTimeRtc) return false
        finishNow(context)
        return true
    }

    /** Khóa màn hình ngay (nếu có quyền Device Admin) + dọn state + notification. */
    fun finishNow(context: Context) {
        android.util.Log.i(TAG, "finishNow() called")

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, LockDeviceAdminReceiver::class.java)
        val adminActive = dpm.isAdminActive(adminComponent)
        android.util.Log.i(
            TAG, "device admin active=$adminActive -> ${if (adminActive) "lockNow()" else "SKIP lockNow"}"
        )
        if (adminActive) {
            runCatching { dpm.lockNow() }
                .onSuccess { android.util.Log.i(TAG, "lockNow() OK") }
                .onFailure { android.util.Log.e(TAG, "lockNow() FAILED", it) }
        }

        TimerRepository.stop(context)
        AlarmScheduler.cancelLockAlarm(context)
        Notifier.ensureChannels(context)

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(Notifier.ID_TIMER)
        runCatching {
            notificationManager.notify(
                Notifier.ID_LOCKED,
                Notifier.lockedNotification(context, adminActive)
            )
        }

        runCatching { context.stopService(Intent(context, CountdownService::class.java)) }
    }
}
