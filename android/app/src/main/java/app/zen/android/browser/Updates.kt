package app.zen.android.browser

import android.content.Context
import android.content.Intent
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class Release(val version: String, val pageUrl: String)

/**
 * The app is not on a store: new versions are GitHub releases tagged
 * `android-vX.Y.Z`, which Obtainium (or this check) picks up. Security fixes
 * mostly arrive through GeckoView, so staying current matters.
 */
object Updates {
    const val REPO_URL = "https://github.com/ericmatte/zen-browser-desktop"
    private const val LATEST_API = "https://api.github.com/repos/ericmatte/zen-browser-desktop/releases/latest"
    const val TAG_PREFIX = "android-v"
    const val CHECK_EVERY_MILLIS = 12 * 60 * 60 * 1000L

    /** Obtainium's "add app" link, prefilled with this repository. */
    const val OBTAINIUM_ADD = "obtainium://add/$REPO_URL"
    val OBTAINIUM_PACKAGES = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")
    const val OBTAINIUM_DOWNLOAD = "https://github.com/ImranR98/Obtainium/releases/latest"

    fun obtainiumInstalled(context: Context): Boolean =
        OBTAINIUM_PACKAGES.any { context.packageManager.getLaunchIntentForPackage(it) != null }

    fun openObtainium(context: Context): Boolean {
        val launch = OBTAINIUM_PACKAGES.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) } ?: return false
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun versionOf(tag: String): String? = tag.removePrefix(TAG_PREFIX).takeIf { tag.startsWith(TAG_PREFIX) && parts(it) != null }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate) ?: return false
        val b = parts(current) ?: return true
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun parse(json: String): Release? = runCatching {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val version = versionOf(o.getString("tag_name")) ?: return null
        Release(version, o.getString("html_url"))
    }.getOrNull()

    fun fetchLatest(): Release? = runCatching {
        val conn = URL(LATEST_API).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Zen-Android")
        try {
            if (conn.responseCode != 200) return null
            parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun parts(version: String): List<Int>? =
        version.split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
}
