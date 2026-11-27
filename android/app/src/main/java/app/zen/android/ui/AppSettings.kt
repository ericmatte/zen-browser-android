package app.zen.android.ui

import android.content.Context
import android.content.SharedPreferences
import app.zen.android.browser.SearchEngine
import app.zen.android.sync.TabItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class TrackingProtection(val label: String, val detail: String) {
    STANDARD("Standard", "Blocks trackers, cross-site cookies and tracking parameters in links, and tells sites not to sell your data"),
    STRICT("Strict", "Also blocks fingerprinters and tracking content; some sites may break"),
}

enum class HttpsOnly(val label: String) { OFF("Off"), PRIVATE("Private tabs only"), ALL("All tabs") }

enum class DnsOverHttps(val label: String, val detail: String) {
    OFF("Off", "Use your network's DNS"),
    DEFAULT("Default protection", "Encrypted DNS, falls back to your network if it fails"),
    MAX("Max protection", "Always encrypted DNS, never falls back"),
}

enum class Autoplay(val label: String) { BLOCK_AUDIBLE("Block audio only"), BLOCK_ALL("Block audio and video"), ALLOW("Allow audio and video") }

enum class SitePermission(val label: String) { ASK("Ask"), BLOCK("Block") }

/** zen.tabs and Firefox's tab cleanup, as one choice. */
enum class TabCleanup(val label: String, val hours: Long?) {
    NEVER("Never", null),
    HOUR("After 1 hour", 1),
    HALF_DAY("After 12 hours", 12),
    DAY("After 1 day", 24),
    WEEK("After 1 week", 24 * 7),
}

data class Preferences(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val searchSuggestions: Boolean = true,
    val suggestionsInPrivate: Boolean = false,
    val historyInSuggestions: Boolean = true,
    val openTabsInSuggestions: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val tintStatusBar: Boolean = true,
    val textScale: Float = 1f,
    val forceZoom: Boolean = false,
    val tracking: TrackingProtection = TrackingProtection.STANDARD,
    val adBlocking: Boolean = true,
    val rejectCookieBanners: Boolean = true,
    val httpsOnly: HttpsOnly = HttpsOnly.PRIVATE,
    val dns: DnsOverHttps = DnsOverHttps.OFF,
    val rememberHistory: Boolean = true,
    val autoplay: Autoplay = Autoplay.BLOCK_AUDIBLE,
    val location: SitePermission = SitePermission.ASK,
    val camera: SitePermission = SitePermission.ASK,
    val microphone: SitePermission = SitePermission.ASK,
    val notifications: SitePermission = SitePermission.ASK,
    val tabCleanup: TabCleanup = TabCleanup.HALF_DAY,
    val haptics: Boolean = true,
    val expandedFolders: Set<String> = emptySet(),
    val collapsedSpaces: Set<String> = emptySet(),
)

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("zen", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Preferences> = _state.asStateFlow()

    private val _essentials = MutableStateFlow(readEssentials())

    /** Essentials stay on this phone: desktop's essentials are not synced down, and these are not synced up. */
    val essentials: StateFlow<List<TabItem>> = _essentials.asStateFlow()

    fun update(change: (Preferences) -> Preferences) {
        val next = change(_state.value)
        _state.value = next
        prefs.edit().apply { write(next) }.apply()
    }

    fun toggleFolder(id: String) = update {
        it.copy(expandedFolders = if (id in it.expandedFolders) it.expandedFolders - id else it.expandedFolders + id)
    }

    fun toggleSpaceCollapsed(uuid: String) = update {
        it.copy(collapsedSpaces = if (uuid in it.collapsedSpaces) it.collapsedSpaces - uuid else it.collapsedSpaces + uuid)
    }

    fun setEssentials(list: List<TabItem>) {
        _essentials.value = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("url", it.url).put("title", it.title)) }
        prefs.edit().putString("essentials", arr.toString()).apply()
    }

    fun string(key: String): String? = prefs.getString(key, null)
    fun putString(key: String, value: String?) = prefs.edit().putString(key, value).apply()

    private fun readEssentials(): List<TabItem> = runCatching {
        val arr = JSONArray(prefs.getString("essentials", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TabItem(o.getString("id"), o.getString("url"), o.optString("title"), pinned = true)
        }
    }.getOrDefault(emptyList())

    private fun SharedPreferences.Editor.write(p: Preferences) {
        putString("searchEngine", p.searchEngine.name)
        putBoolean("searchSuggestions", p.searchSuggestions)
        putBoolean("suggestionsInPrivate", p.suggestionsInPrivate)
        putBoolean("historyInSuggestions", p.historyInSuggestions)
        putBoolean("openTabsInSuggestions", p.openTabsInSuggestions)
        putString("themeMode", p.themeMode.name)
        putBoolean("tintStatusBar", p.tintStatusBar)
        putFloat("textScale", p.textScale)
        putBoolean("forceZoom", p.forceZoom)
        putString("tracking", p.tracking.name)
        putBoolean("adBlocking", p.adBlocking)
        putBoolean("rejectCookieBanners", p.rejectCookieBanners)
        putString("httpsOnly", p.httpsOnly.name)
        putString("dns", p.dns.name)
        putBoolean("rememberHistory", p.rememberHistory)
        putString("autoplay", p.autoplay.name)
        putString("location", p.location.name)
        putString("camera", p.camera.name)
        putString("microphone", p.microphone.name)
        putString("notifications", p.notifications.name)
        putString("tabCleanup", p.tabCleanup.name)
        putBoolean("haptics", p.haptics)
        putStringSet("expandedFolders", p.expandedFolders)
        putStringSet("collapsedSpaces", p.collapsedSpaces)
    }

    private fun read(): Preferences {
        val d = Preferences()
        return Preferences(
            searchEngine = enumOr(prefs.getString("searchEngine", null), d.searchEngine),
            searchSuggestions = prefs.getBoolean("searchSuggestions", d.searchSuggestions),
            suggestionsInPrivate = prefs.getBoolean("suggestionsInPrivate", d.suggestionsInPrivate),
            historyInSuggestions = prefs.getBoolean("historyInSuggestions", d.historyInSuggestions),
            openTabsInSuggestions = prefs.getBoolean("openTabsInSuggestions", d.openTabsInSuggestions),
            themeMode = enumOr(prefs.getString("themeMode", null), d.themeMode),
            tintStatusBar = prefs.getBoolean("tintStatusBar", d.tintStatusBar),
            textScale = prefs.getFloat("textScale", d.textScale),
            forceZoom = prefs.getBoolean("forceZoom", d.forceZoom),
            tracking = enumOr(prefs.getString("tracking", null), d.tracking),
            adBlocking = prefs.getBoolean("adBlocking", d.adBlocking),
            rejectCookieBanners = prefs.getBoolean("rejectCookieBanners", d.rejectCookieBanners),
            httpsOnly = enumOr(prefs.getString("httpsOnly", null), d.httpsOnly),
            dns = enumOr(prefs.getString("dns", null), d.dns),
            rememberHistory = prefs.getBoolean("rememberHistory", d.rememberHistory),
            autoplay = enumOr(prefs.getString("autoplay", null), d.autoplay),
            location = enumOr(prefs.getString("location", null), d.location),
            camera = enumOr(prefs.getString("camera", null), d.camera),
            microphone = enumOr(prefs.getString("microphone", null), d.microphone),
            notifications = enumOr(prefs.getString("notifications", null), d.notifications),
            tabCleanup = enumOr(prefs.getString("tabCleanup", null), d.tabCleanup),
            haptics = prefs.getBoolean("haptics", d.haptics),
            expandedFolders = prefs.getStringSet("expandedFolders", emptySet()).orEmpty().toSet(),
            collapsedSpaces = prefs.getStringSet("collapsedSpaces", emptySet()).orEmpty().toSet(),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
