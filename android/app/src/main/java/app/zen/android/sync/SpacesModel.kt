package app.zen.android.sync

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Mirrors RECORD_KINDS in src/zen/sync/ZenSpacesSyncModel.sys.mjs. */
object Kind {
    const val CONTAINER = "container"
    const val SPACE = "space"
    const val TAB = "tab"
    const val FOLDER = "folder"
    const val SPLIT = "split"
    const val LAYOUT = "layout"
}

const val LAYOUT_ID = "layout"

sealed interface SidebarItem {
    val id: String
}

data class TabItem(
    override val id: String,
    val url: String,
    val title: String,
    val pinned: Boolean,
) : SidebarItem

data class FolderItem(override val id: String, val name: String, val items: List<SidebarItem>) : SidebarItem

data class Space(val uuid: String, val name: String, val icon: String?, val items: List<SidebarItem>) {
    val tabs: List<TabItem> get() = items.flatMap { it.flattenTabs() }
}

fun SidebarItem.flattenTabs(): List<TabItem> = when (this) {
    is TabItem -> listOf(this)
    is FolderItem -> items.flatMap { it.flattenTabs() }
}

/**
 * A local edit made on the phone. Edits are stored as intents and replayed on
 * top of the freshest server state on every sync, so the phone never uploads
 * a stale copy of a record the desktop changed in the meantime.
 */
sealed interface LocalEdit {
    fun toJson(): JSONObject

    data class OpenTab(val tabId: String, val spaceUuid: String, val url: String, val title: String) : LocalEdit {
        override fun toJson() = JSONObject().put("op", "open").put("tabId", tabId)
            .put("space", spaceUuid).put("url", url).put("title", title)
    }

    data class UpdateTab(val tabId: String, val url: String, val title: String) : LocalEdit {
        override fun toJson() = JSONObject().put("op", "update").put("tabId", tabId).put("url", url).put("title", title)
    }

    data class CloseTab(val tabId: String) : LocalEdit {
        override fun toJson() = JSONObject().put("op", "close").put("tabId", tabId)
    }

    companion object {
        fun fromJson(json: JSONObject): LocalEdit = when (json.getString("op")) {
            "open" -> OpenTab(json.getString("tabId"), json.getString("space"), json.getString("url"), json.getString("title"))
            "update" -> UpdateTab(json.getString("tabId"), json.getString("url"), json.getString("title"))
            "close" -> CloseTab(json.getString("tabId"))
            else -> throw IllegalArgumentException("Unknown edit ${json.getString("op")}")
        }
    }
}

/** Immutable snapshot of the `spaces` collection, keyed by record id. */
class SpacesState(val records: Map<String, JSONObject> = emptyMap()) {

    private fun data(id: String): JSONObject? = records[id]?.optJSONObject("data")
    private fun kind(id: String): String? = records[id]?.optString("kind")

    fun withIncoming(incoming: List<Bso>): SpacesState {
        val next = records.toMutableMap()
        incoming.forEach { if (it.deleted) next.remove(it.id) else next[it.id] = it.cleartext!! }
        return SpacesState(next)
    }

    fun spaces(): List<Space> {
        val ordered = data(LAYOUT_ID)?.optJSONArray("spaces")?.strings()
            ?: records.filterValues { it.optString("kind") == Kind.SPACE }.keys.toList()
        return ordered.mapNotNull { uuid ->
            val d = data(uuid) ?: return@mapNotNull null
            Space(
                uuid = uuid,
                name = d.optString("name").ifEmpty { "Space" },
                icon = d.optString("icon").takeIf { it.isNotEmpty() && it != "null" },
                items = items(d.optJSONArray("children")),
            )
        }
    }

    fun essentials(): List<TabItem> {
        val byContainer = data(LAYOUT_ID)?.optJSONObject("essentials") ?: return emptyList()
        return byContainer.keys().asSequence()
            .flatMap { byContainer.getJSONArray(it).strings() }
            .mapNotNull { tab(it) }
            .toList()
    }

    fun tab(id: String): TabItem? {
        if (kind(id) != Kind.TAB) return null
        val d = data(id) ?: return null
        return TabItem(id, d.optString("url"), d.optString("title").ifEmpty { d.optString("url") }, d.optBoolean("pinned"))
    }

    private fun items(children: JSONArray?, depth: Int = 0): List<SidebarItem> {
        if (children == null || depth > 8) return emptyList()
        return children.strings().flatMap { id ->
            when (kind(id)) {
                Kind.TAB -> listOfNotNull(tab(id))
                Kind.FOLDER -> {
                    val d = data(id)!!
                    listOf(FolderItem(id, d.optString("name").ifEmpty { "Folder" }, items(d.optJSONArray("children"), depth + 1)))
                }
                Kind.SPLIT -> data(id)!!.optJSONArray("tabs").strings().mapNotNull { tab(it) }
                else -> emptyList()
            }
        }
    }

    /**
     * Applies [edits] and returns the new state plus the cleartexts to upload
     * (a null cleartext is a tombstone).
     */
    fun apply(edits: List<LocalEdit>): Pair<SpacesState, Map<String, JSONObject?>> {
        val next = records.mapValues { JSONObject(it.value.toString()) }.toMutableMap()
        val changed = linkedMapOf<String, JSONObject?>()

        fun parentOf(tabId: String): String? = next.entries.firstOrNull { (_, rec) ->
            rec.optString("kind") in setOf(Kind.SPACE, Kind.FOLDER) &&
                rec.optJSONObject("data")?.optJSONArray("children")?.strings()?.contains(tabId) == true
        }?.key

        for (edit in edits) {
            when (edit) {
                is LocalEdit.OpenTab -> {
                    val space = next[edit.spaceUuid] ?: continue
                    if (next.containsKey(edit.tabId)) continue
                    val spaceData = space.getJSONObject("data")
                    next[edit.tabId] = tabRecord(edit, spaceData.opt("containerGuid"))
                    val children = spaceData.optJSONArray("children") ?: JSONArray().also { spaceData.put("children", it) }
                    children.put(edit.tabId)
                    changed[edit.tabId] = next[edit.tabId]
                    changed[edit.spaceUuid] = space
                }
                is LocalEdit.UpdateTab -> {
                    val rec = next[edit.tabId] ?: continue
                    val d = rec.getJSONObject("data")
                    if (d.optString("url") == edit.url && d.optString("title") == edit.title) continue
                    d.put("url", edit.url).put("title", edit.title)
                    changed[edit.tabId] = rec
                }
                is LocalEdit.CloseTab -> {
                    if (!next.containsKey(edit.tabId)) continue
                    parentOf(edit.tabId)?.let { parentId ->
                        val parent = next.getValue(parentId)
                        val d = parent.getJSONObject("data")
                        d.put("children", JSONArray(d.getJSONArray("children").strings().filter { it != edit.tabId }))
                        changed[parentId] = parent
                    }
                    next.remove(edit.tabId)
                    changed[edit.tabId] = null
                }
            }
        }
        return SpacesState(next) to changed
    }

    fun toJson(): JSONObject = JSONObject().also { out -> records.forEach { (k, v) -> out.put(k, v) } }

    companion object {
        fun fromJson(json: JSONObject) = SpacesState(json.keys().asSequence().associateWith { json.getJSONObject(it) })

        fun newTabId(): String = UUID.randomUUID().toString()

        private fun tabRecord(edit: LocalEdit.OpenTab, containerGuid: Any?) = JSONObject()
            .put("id", edit.tabId)
            .put("kind", Kind.TAB)
            .put(
                "data", JSONObject()
                    .put("tabId", edit.tabId)
                    .put("url", edit.url)
                    .put("title", edit.title)
                    .put("icon", "")
                    .put("containerGuid", containerGuid ?: JSONObject.NULL)
                    .put("essential", false)
                    .put("pinned", false)
                    .put("workspaceUuid", edit.spaceUuid)
                    .put("folderId", JSONObject.NULL)
                    .put("staticLabel", JSONObject.NULL)
                    .put("hasStaticIcon", false)
                    .put("defaultContainer", false)
            )
    }
}

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }
