package com.khahdihdz.locktimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Bắn khi alarm hẹn giờ đổ chuông. Đây là cơ chế dự phòng khi service bị hệ
 * thống thu hồi — logic khóa máy dùng chung qua CountdownFinisher.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_LOCK = "com.khahdihdz.locktimer.action.LOCK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOCK) return
        CountdownFinisher.finishNow(context)
    }
}
