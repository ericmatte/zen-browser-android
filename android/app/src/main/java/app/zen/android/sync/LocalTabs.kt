package app.zen.android.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Temporary (unpinned) tabs never sync: desktop keeps its own, and the phone
 * keeps one list that every space shows below the separator. Only pinned
 * tabs, folders and spaces travel through the server.
 */
object LocalTabs {
    /** The spaces as the phone shows them: synced pinned items, then the phone's own temporary tabs. */
    fun merge(spaces: List<Space>, local: List<TabItem>): List<Space> {
        // While a tab is being pinned or unpinned it briefly exists on both sides; the synced copy wins.
        val synced = spaces.flatMap { it.tabs }.filter { it.pinned }.map { it.id }.toSet()
        val temporary = local.filter { it.id !in synced }.map { it.copy(pinned = false) }
        return spaces.map { space -> space.copy(items = space.items.filter { it !is TabItem || it.pinned } + temporary) }
    }

    /** Puts [tab] before [beforeId], or last when that tab is not in the list. */
    fun insert(list: List<TabItem>, tab: TabItem, beforeId: String?): List<TabItem> {
        val rest = list.filter { it.id != tab.id }
        val at = beforeId?.let { id -> rest.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: rest.size
        return rest.toMutableList().apply { add(at, tab) }
    }

    /**
     * The phone's temporary tabs that earlier versions synced. They stay on
     * desktop untouched; the phone keeps copies under new ids, so the two
     * never act on each other again.
     */
    fun migrate(spaces: List<Space>, openedHere: Set<String>, newId: () -> String): List<TabItem> =
        spaces.flatMap { it.items }
            .filterIsInstance<TabItem>()
            .filter { !it.pinned && it.id in openedHere }
            .distinctBy { it.id }
            .map { it.copy(id = newId(), pinned = false) }

    fun encode(list: List<TabItem>): String = JSONArray().also { arr ->
        list.forEach { arr.put(JSONObject().put("id", it.id).put("url", it.url).put("title", it.title)) }
    }.toString()

    fun decode(json: String?): List<TabItem> = runCatching {
        val arr = JSONArray(json ?: return emptyList())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TabItem(o.getString("id"), o.getString("url"), o.optString("title"), pinned = false)
        }
    }.getOrDefault(emptyList())
}
