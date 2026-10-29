package com.khahdihdz.locktimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Alarm dự phòng. Chỉ khóa khi đã đến deadline đã lưu; nếu alarm bị phát sớm
 * (ví dụ đồng hồ hệ thống thay đổi), đặt lại alarm thay vì khóa máy trước giờ.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_LOCK = "com.khahdihdz.locktimer.action.LOCK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOCK) return

        val endTime = TimerRepository.endTimeRtc(context)
        if (endTime <= 0L) {
            Log.i(CountdownFinisher.TAG, "AlarmReceiver: no active timer; ignore")
            return
        }

        if (CountdownFinisher.finishIfDue(context)) {
            Log.i(CountdownFinisher.TAG, "AlarmReceiver: deadline reached; finishing timer")
        } else {
            Log.i(CountdownFinisher.TAG, "AlarmReceiver: fired early; rescheduling deadline=$endTime")
            AlarmScheduler.scheduleLockAlarm(context, endTime)
        }
    }
}
