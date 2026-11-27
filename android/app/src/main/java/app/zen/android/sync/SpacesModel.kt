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
    val icon: String? = null,
) : SidebarItem

data class FolderItem(
    override val id: String,
    val name: String,
    val items: List<SidebarItem>,
    val icon: ZenIcon? = null,
) : SidebarItem

data class Space(
    val uuid: String,
    val name: String,
    val icon: ZenIcon?,
    val items: List<SidebarItem>,
    val theme: SpaceTheme? = null,
) {
    val tabs: List<TabItem> get() = items.flatMap { it.flattenTabs() }
    val pinnedItems: List<SidebarItem> get() = items.filter { it !is TabItem || it.pinned }
    val regularTabs: List<TabItem> get() = items.filterIsInstance<TabItem>().filter { !it.pinned }
}

/** A space or folder icon as stored by desktop: an emoji or one of Zen's bundled "selectable" SVGs. */
sealed interface ZenIcon {
    data class Emoji(val text: String) : ZenIcon
    data class Bundled(val name: String) : ZenIcon

    companion object {
        private val bundled = Regex("""zen-icons/selectable/([\w-]+)\.svg""")

        fun parse(raw: String?): ZenIcon? {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty() || value == "null") return null
            bundled.find(value)?.let { return Bundled(it.groupValues[1]) }
            return if (value.contains(':') || value.contains('/')) null else Emoji(value)
        }
    }
}

/** The gradient a space uses on desktop (ZenGradientGenerator.getTheme). Colors are ARGB, primary first. */
data class SpaceTheme(val colors: List<Long>, val opacity: Float, val custom: Boolean = false, val dominant: Long = colors.first()) {
    companion object {
        private val numbers = Regex("""[\d.]+""")

        fun parse(json: JSONObject?): SpaceTheme? {
            val list = json?.optJSONArray("gradientColors") ?: return null
            val raw = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
                .mapNotNull { dot -> parseColor(dot.opt("c"))?.let { Triple(it, dot.optBoolean("isPrimary"), dot.optBoolean("isCustom")) } }
            if (raw.isEmpty()) return null
            // ZenGradientGenerator.getPrimaryColor: the primary dot, else the middle one.
            val dominant = (raw.firstOrNull { it.second } ?: raw[raw.size / 2]).first
            val dots = raw.sortedByDescending { it.second }
            return SpaceTheme(
                colors = dots.map { it.first },
                opacity = json.optDouble("opacity", 0.5).toFloat().coerceIn(0f, 1f),
                custom = dots.any { it.third },
                dominant = dominant,
            )
        }

        fun parseColor(value: Any?): Long? = when (value) {
            is JSONArray -> if (value.length() >= 3) rgb(value.optInt(0), value.optInt(1), value.optInt(2)) else null
            is String -> parseCssColor(value)
            else -> null
        }

        private fun rgb(r: Int, g: Int, b: Int): Long =
            0xFF000000L or (r.coerceIn(0, 255).toLong() shl 16) or (g.coerceIn(0, 255).toLong() shl 8) or b.coerceIn(0, 255).toLong()

        private fun parseCssColor(css: String): Long? {
            val text = css.trim()
            if (text.startsWith("#")) {
                val hex = text.drop(1)
                val full = when (hex.length) {
                    3, 4 -> hex.take(3).map { "$it$it" }.joinToString("")
                    6, 8 -> hex.take(6)
                    else -> return null
                }
                return full.toLongOrNull(16)?.let { 0xFF000000L or it }
            }
            val parts = numbers.findAll(text.substringAfter('(')).map { it.value.toDouble().toInt() }.toList()
            return if (text.startsWith("rgb") && parts.size >= 3) rgb(parts[0], parts[1], parts[2]) else null
        }
    }
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

    /**
     * Puts [itemId] in [parentId]'s children right before [beforeId] (or last).
     * Anchoring on a neighbour, not an index, keeps the intent valid when the
     * desktop added or removed siblings before the replay.
     */
    data class MoveItem(val itemId: String, val parentId: String, val beforeId: String?, val pinned: Boolean?) : LocalEdit {
        override fun toJson() = JSONObject().put("op", "move").put("itemId", itemId).put("parent", parentId)
            .put("before", beforeId ?: JSONObject.NULL).put("pinned", pinned ?: JSONObject.NULL)
    }

    data class RenameSpace(val uuid: String, val name: String) : LocalEdit {
        override fun toJson() = JSONObject().put("op", "renameSpace").put("uuid", uuid).put("name", name)
    }

    data class RenameFolder(val folderId: String, val name: String) : LocalEdit {
        override fun toJson() = JSONObject().put("op", "renameFolder").put("folderId", folderId).put("name", name)
    }

    companion object {
        /** Builds the move for [itemId] once the sidebar shows [pinnedIds] above the separator and [regularIds] below. */
        fun placing(spaceUuid: String, itemId: String, pinnedIds: List<String>, regularIds: List<String>): MoveItem {
            val all = pinnedIds + regularIds
            return MoveItem(itemId, spaceUuid, all.getOrNull(all.indexOf(itemId) + 1), itemId in pinnedIds)
        }

        fun fromJson(json: JSONObject): LocalEdit = when (json.getString("op")) {
            "open" -> OpenTab(json.getString("tabId"), json.getString("space"), json.getString("url"), json.getString("title"))
            "update" -> UpdateTab(json.getString("tabId"), json.getString("url"), json.getString("title"))
            "close" -> CloseTab(json.getString("tabId"))
            "renameSpace" -> RenameSpace(json.getString("uuid"), json.getString("name"))
            "renameFolder" -> RenameFolder(json.getString("folderId"), json.getString("name"))
            "move" -> MoveItem(
                json.getString("itemId"),
                json.getString("parent"),
                json.optString("before").takeUnless { json.isNull("before") },
                if (json.isNull("pinned")) null else json.getBoolean("pinned"),
            )
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
                icon = ZenIcon.parse(d.optString("icon")),
                items = items(d.optJSONArray("children")),
                theme = SpaceTheme.parse(d.optJSONObject("theme")),
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
        return TabItem(
            id,
            d.optString("url"),
            d.optString("staticLabel").takeIf { it.isNotEmpty() && !d.isNull("staticLabel") }
                ?: d.optString("title").ifEmpty { d.optString("url") },
            d.optBoolean("pinned"),
            d.optString("icon").takeIf { it.startsWith("data:") },
        )
    }

    private fun items(children: JSONArray?, depth: Int = 0): List<SidebarItem> {
        if (children == null || depth > 8) return emptyList()
        return children.strings().flatMap { id ->
            when (kind(id)) {
                Kind.TAB -> listOfNotNull(tab(id))
                Kind.FOLDER -> {
                    val d = data(id)!!
                    listOf(
                        FolderItem(
                            id,
                            d.optString("name").ifEmpty { "Folder" },
                            items(d.optJSONArray("children"), depth + 1),
                            ZenIcon.parse(d.optString("icon")),
                        )
                    )
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

        fun setChildren(parentId: String, children: List<String>) {
            val parent = next.getValue(parentId)
            parent.getJSONObject("data").put("children", JSONArray(children))
            changed[parentId] = parent
        }

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
                is LocalEdit.RenameSpace -> rename(next, changed, edit.uuid, Kind.SPACE, edit.name)
                is LocalEdit.RenameFolder -> rename(next, changed, edit.folderId, Kind.FOLDER, edit.name)
                is LocalEdit.MoveItem -> {
                    val item = next[edit.itemId] ?: continue
                    val target = next[edit.parentId] ?: continue
                    if (target.optString("kind") !in setOf(Kind.SPACE, Kind.FOLDER)) continue
                    val oldParent = parentOf(edit.itemId)
                    val before = target.getJSONObject("data").optJSONArray("children").strings()
                    if (oldParent != null && oldParent != edit.parentId) {
                        setChildren(oldParent, next.getValue(oldParent).getJSONObject("data").getJSONArray("children").strings().filter { it != edit.itemId })
                    }
                    val siblings = before.filter { it != edit.itemId }.toMutableList()
                    val at = edit.beforeId?.let { siblings.indexOf(it) }?.takeIf { it >= 0 } ?: siblings.size
                    siblings.add(at, edit.itemId)
                    if (siblings != before) setChildren(edit.parentId, siblings)
                    if (item.optString("kind") == Kind.TAB) {
                        val d = item.getJSONObject("data")
                        val folderId = if (target.optString("kind") == Kind.FOLDER) edit.parentId else null
                        val pinned = if (folderId != null) true else edit.pinned ?: d.optBoolean("pinned")
                        val folderChanged = d.opt("folderId").let { if (it == JSONObject.NULL) null else it } != folderId
                        if (d.optBoolean("pinned") != pinned || folderChanged) {
                            d.put("pinned", pinned).put("folderId", folderId ?: JSONObject.NULL)
                            changed[edit.itemId] = item
                        }
                    }
                }
            }
        }
        return SpacesState(next) to changed
    }

    private fun rename(next: MutableMap<String, JSONObject>, changed: MutableMap<String, JSONObject?>, id: String, kind: String, name: String) {
        val rec = next[id]?.takeIf { it.optString("kind") == kind } ?: return
        val d = rec.getJSONObject("data")
        val clean = name.trim()
        if (clean.isEmpty() || d.optString("name") == clean) return
        d.put("name", clean)
        changed[id] = rec
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
