package app.zen.android.browser

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Visit(val url: String, val title: String, val visits: Int, val lastVisit: Long)

/**
 * Pages visited outside private mode, kept on this device only. Ranking
 * follows the spirit of Firefox's frecency: often and recently visited pages
 * first, with host-prefix matches ahead of matches deeper in the URL.
 */
class History(private val file: File?, private val limit: Int = 2000) {
    private val entries = LinkedHashMap<String, Visit>()

    init {
        file?.takeIf { it.exists() }?.let { f ->
            runCatching {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    entries[o.getString("u")] = Visit(o.getString("u"), o.optString("t"), o.optInt("v", 1), o.optLong("l"))
                }
            }
        }
    }

    @Synchronized
    fun record(url: String, title: String, now: Long = System.currentTimeMillis()) {
        if (!url.startsWith("http")) return
        val previous = entries.remove(url)
        entries[url] = Visit(url, title.ifBlank { previous?.title ?: url }, (previous?.visits ?: 0) + 1, now)
        while (entries.size > limit) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun search(query: String, max: Int = 5, now: Long = System.currentTimeMillis()): List<Visit> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries.values
            .filter { v -> words.all { w -> v.title.lowercase().contains(w) || v.url.lowercase().contains(w) } }
            .sortedByDescending { score(it, words.first(), now) }
            .take(max)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun save() {
        val f = file ?: return
        val arr = JSONArray()
        entries.values.forEach { arr.put(JSONObject().put("u", it.url).put("t", it.title).put("v", it.visits).put("l", it.lastVisit)) }
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(f)
    }

    private fun score(v: Visit, first: String, now: Long): Double {
        val days = (now - v.lastVisit).coerceAtLeast(0) / 86_400_000.0
        val recency = 1.0 / (1.0 + days / 30.0)
        val hostMatch = UrlInput.display(v.url).lowercase().startsWith(first)
        return v.visits * recency + if (hostMatch) 100.0 else 0.0
    }
}
