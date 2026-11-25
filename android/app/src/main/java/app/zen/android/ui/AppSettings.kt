package app.zen.android.ui

import android.content.Context
import app.zen.android.browser.SearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class Preferences(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val tintStatusBar: Boolean = true,
    val expandedFolders: Set<String> = emptySet(),
)

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("zen", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Preferences> = _state.asStateFlow()

    fun update(change: (Preferences) -> Preferences) {
        val next = change(_state.value)
        _state.value = next
        prefs.edit()
            .putString("searchEngine", next.searchEngine.name)
            .putString("themeMode", next.themeMode.name)
            .putBoolean("tintStatusBar", next.tintStatusBar)
            .putStringSet("expandedFolders", next.expandedFolders)
            .apply()
    }

    fun toggleFolder(id: String) = update {
        it.copy(expandedFolders = if (id in it.expandedFolders) it.expandedFolders - id else it.expandedFolders + id)
    }

    private fun read() = Preferences(
        searchEngine = enumOr(prefs.getString("searchEngine", null), SearchEngine.GOOGLE),
        themeMode = enumOr(prefs.getString("themeMode", null), ThemeMode.SYSTEM),
        tintStatusBar = prefs.getBoolean("tintStatusBar", true),
        expandedFolders = prefs.getStringSet("expandedFolders", emptySet()).orEmpty().toSet(),
    )

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
