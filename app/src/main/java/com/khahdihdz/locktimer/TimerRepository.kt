package com.khahdihdz.locktimer

import android.content.Context
import android.content.SharedPreferences

/**
 * Lưu trạng thái hẹn giờ hiện tại (thời điểm kết thúc tuyệt đối tính theo epoch RTC).
 * Dùng thời điểm kết thúc thay vì đếm nhịp tick để giờ không trôi khi app bị doze.
 */
object TimerRepository {

    private const val PREFS_NAME = "lock_timer_prefs"
    private const val KEY_END_TIME_RTC = "end_time_rtc"
    private const val KEY_DURATION_MILLIS = "duration_millis"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isRunning(context: Context): Boolean =
        prefs(context).getLong(KEY_END_TIME_RTC, 0L) > System.currentTimeMillis()

    fun endTimeRtc(context: Context): Long =
        prefs(context).getLong(KEY_END_TIME_RTC, 0L)

    fun durationMillis(context: Context): Long =
        prefs(context).getLong(KEY_DURATION_MILLIS, 0L)

    fun start(context: Context, durationMillis: Long) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putLong(KEY_END_TIME_RTC, now + durationMillis)
            .putLong(KEY_DURATION_MILLIS, durationMillis)
            .apply()
    }

    fun stop(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
