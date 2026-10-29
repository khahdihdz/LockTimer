package com.khahdihdz.locktimer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Checks GitHub Releases when the app opens and notifies once for each newer release.
 * Network failures are silent; the timer never depends on this check.
 */
object VersionUpdateChecker {
    private const val RELEASE_API =
        "https://api.github.com/repos/khahdihdz/LockTimer/releases/latest"
    private const val RELEASES_URL = "https://github.com/khahdihdz/LockTimer/releases"
    private const val CHANNEL_ID = "locktimer_updates"
    private const val NOTIFICATION_ID = 1101
    private const val PREFS = "version_updates"
    private const val KEY_LAST_NOTIFIED = "last_notified_tag"

    private val executor = Executors.newSingleThreadExecutor()

    fun check(context: Context) {
        val appContext = context.applicationContext
        executor.execute {
            val release = runCatching {
                val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "LockTimer-Android")
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            }.getOrNull() ?: return@execute

            val tag = release.optString("tag_name").trim()
            val htmlUrl = release.optString("html_url").ifBlank { RELEASES_URL }
            val latestVersion = tag.removePrefix("v")
            val currentVersion = runCatching {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
            }.getOrNull().orEmpty()

            if (tag.isBlank() || compareVersions(latestVersion, currentVersion) <= 0) return@execute

            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST_NOTIFIED, null) == tag) return@execute
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    appContext, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return@execute

            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        appContext.getString(R.string.update_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = appContext.getString(R.string.update_channel_description)
                    }
                )
            }

            val openRelease = PendingIntent.getActivity(
                appContext,
                NOTIFICATION_ID,
                Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(appContext.getString(R.string.update_available_title, latestVersion))
                .setContentText(appContext.getString(R.string.update_available_text))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(appContext.getString(R.string.update_available_text))
                )
                .setContentIntent(openRelease)
                .setAutoCancel(true)
                .build()

            manager.notify(NOTIFICATION_ID, notification)
            prefs.edit().putString(KEY_LAST_NOTIFIED, tag).apply()
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }
        val count = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until count) {
            val a = leftParts.getOrElse(index) { 0 }
            val b = rightParts.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }
}
