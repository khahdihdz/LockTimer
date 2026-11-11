package com.khahdihdz.locktimer

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val assetName: String
)

object UpdateChecker {
    private const val RELEASE_API =
        "https://api.github.com/repos/khahdihdz/LockTimer/releases/latest"

    fun fetchLatest(): UpdateInfo? {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LockTimer-Android")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

        return try {
            if (connection.responseCode !in 200..299) return null
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = release.optString("tag_name").removePrefix("v").trim()
            if (tag.isBlank() || !isNewer(tag, BuildConfig.VERSION_NAME)) return null

            val assets = release.optJSONArray("assets") ?: return null
            var apkName: String? = null
            var apkUrl: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.startsWith("LockTimer-") && name.endsWith(".apk") &&
                    url.startsWith("https://github.com/") &&
                    url.contains("/releases/download/")
                ) {
                    apkName = name
                    apkUrl = url
                    break
                }
            }
            if (apkName == null || apkUrl == null) return null

            UpdateInfo(
                versionName = tag,
                releaseNotes = release.optString("body").take(500),
                downloadUrl = apkUrl,
                releaseUrl = release.optString("html_url"),
                assetName = apkName
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(index) { 0 }
            val c = currentParts.getOrElse(index) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
