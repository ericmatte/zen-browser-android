package app.zen.android.browser

import java.io.File
import java.util.concurrent.Executor

/**
 * Keeps each tab's Gecko session state (back/forward history, scroll
 * position, form contents) on disk, so a tab reloaded after being evicted
 * from memory, or after Android stopped the app, comes back where it was.
 * Private tabs are never written.
 */
class TabStateStore(private val dir: File, private val io: Executor) {
    private val latest = mutableMapOf<String, String>()
    private val queued = mutableSetOf<String>()

    fun load(tabId: String): String? = synchronized(this) { latest[tabId] } ?: file(tabId)?.takeIf { it.exists() }?.readText()

    /** Called on every state change; only the newest state of a burst is written. */
    fun save(tabId: String, state: String) {
        if (isPrivateTab(tabId)) return
        val schedule = synchronized(this) {
            latest[tabId] = state
            queued.add(tabId)
        }
        if (schedule) io.execute { write(tabId) }
    }

    fun delete(tabId: String) {
        synchronized(this) {
            latest.remove(tabId)
            queued.remove(tabId)
        }
        io.execute { file(tabId)?.delete() }
    }

    private fun write(tabId: String) {
        val state = synchronized(this) {
            queued.remove(tabId)
            latest[tabId]
        } ?: return
        val target = file(tabId) ?: return
        dir.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(state)
        tmp.renameTo(target)
    }

    /** Tab ids are UUIDs or prefixed UUIDs; anything else is refused rather than used as a path. */
    private fun file(tabId: String): File? = tabId.takeIf { SAFE.matches(it) }?.let { File(dir, "$it.json") }

    private companion object {
        val SAFE = Regex("[A-Za-z0-9_:.-]{1,80}")
    }
}

/**
 * What is saved for a tab: Gecko's own session state (history, form data)
 * and the scroll offset, which Gecko's state does not reliably carry on Android.
 */
data class TabSnapshot(val gecko: String?, val scrollY: Int) {
    fun encode(): String = org.json.JSONObject().put("gecko", gecko ?: org.json.JSONObject.NULL).put("scrollY", scrollY).toString()

    companion object {
        fun decode(text: String?): TabSnapshot? = runCatching {
            val json = org.json.JSONObject(text ?: return null)
            // Files written before the scroll offset was kept hold Gecko's state alone.
            if (json.has("history")) return TabSnapshot(text, 0)
            TabSnapshot(json.optString("gecko").takeUnless { json.isNull("gecko") || it.isEmpty() }, json.optInt("scrollY"))
        }.getOrNull()
    }
}
