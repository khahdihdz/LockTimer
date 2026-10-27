package com.khahdihdz.locktimer

import java.util.Locale

/** Format thời gian còn lại: hh:mm:ss khi >= 1 giờ, ngược lại mm:ss. */
fun formatRemaining(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
