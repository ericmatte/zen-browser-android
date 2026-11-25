package app.zen.android.sync

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

fun record(id: String, kind: String, data: JSONObject) = JSONObject().put("id", id).put("kind", kind).put("data", data)

fun space(uuid: String, name: String, vararg children: String) =
    record(uuid, Kind.SPACE, JSONObject().put("uuid", uuid).put("name", name).put("icon", "🏠").put("containerGuid", JSONObject.NULL).put("children", JSONArray(children.toList())))

fun tab(id: String, url: String, pinned: Boolean = false, essential: Boolean = false, workspace: String? = null) =
    record(id, Kind.TAB, JSONObject().put("tabId", id).put("url", url).put("title", "T-$id").put("pinned", pinned).put("essential", essential).put("workspaceUuid", workspace ?: JSONObject.NULL))

fun folder(id: String, name: String, vararg children: String) =
    record(id, Kind.FOLDER, JSONObject().put("folderId", id).put("name", name).put("children", JSONArray(children.toList())))

fun layout(spaces: List<String>, essentials: List<String> = emptyList()) =
    record(LAYOUT_ID, Kind.LAYOUT, JSONObject().put("spaces", JSONArray(spaces)).put("essentials", JSONObject().put("default", JSONArray(essentials))))

fun stateOf(vararg recs: JSONObject) = SpacesState(recs.associateBy { it.getString("id") })

fun bso(rec: JSONObject) = Bso(rec.getString("id"), rec, 1.0)

class SpacesModelTest {

    private val desktop = stateOf(
        layout(listOf("s2", "s1"), essentials = listOf("e1")),
        space("s1", "Work", "f1", "t3"),
        space("s2", "Personal", "t1"),
        folder("f1", "Docs", "t2"),
        tab("t1", "https://a.com", workspace = "s2"),
        tab("t2", "https://b.com", pinned = true, workspace = "s1"),
        tab("t3", "https://c.com", workspace = "s1"),
        tab("e1", "https://mail.com", pinned = true, essential = true),
    )

    @Test
    fun `spaces follow the desktop layout order and nest folders`() {
        val spaces = desktop.spaces()
        assertEquals(listOf("Personal", "Work"), spaces.map { it.name })
        val work = spaces[1]
        assertEquals("Docs", (work.items[0] as FolderItem).name)
        assertEquals(listOf("t2", "t3"), work.tabs.map { it.id })
    }

    @Test
    fun `essentials are read from the layout record`() {
        assertEquals(listOf("https://mail.com"), desktop.essentials().map { it.url })
    }

    @Test
    fun `opening a tab adds a tab record and appends it to the space children`() {
        val (next, changes) = desktop.apply(listOf(LocalEdit.OpenTab("n1", "s2", "https://new.com", "New")))
        assertEquals(listOf("t1", "n1"), next.spaces()[0].tabs.map { it.id })
        assertEquals(setOf("n1", "s2"), changes.keys)
        val data = changes.getValue("n1")!!.getJSONObject("data")
        assertEquals("s2", data.getString("workspaceUuid"))
        assertFalse(data.getBoolean("pinned"))
    }

    @Test
    fun `closing a tab inside a folder tombstones it and updates the folder`() {
        val (next, changes) = desktop.apply(listOf(LocalEdit.CloseTab("t2")))
        assertNull(changes.getValue("t2"))
        assertTrue(changes.containsKey("f1"))
        assertEquals(listOf("t3"), next.spaces()[1].tabs.map { it.id })
    }

    @Test
    fun `updating a tab to identical values produces no change`() {
        val (_, changes) = desktop.apply(listOf(LocalEdit.UpdateTab("t1", "https://a.com", "T-t1")))
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `incoming tombstones remove records`() {
        val next = desktop.withIncoming(listOf(Bso("t1", JSONObject().put("id", "t1").put("deleted", true), 2.0)))
        assertNull(next.tab("t1"))
        assertTrue(next.spaces()[0].tabs.isEmpty())
    }

    @Test
    fun `sync replays phone edits on top of newer desktop changes instead of overwriting them`() = runBlocking {
        val uploads = mutableListOf<List<Pair<String, JSONObject>>>()
        var serverRecords = listOf(layout(listOf("s1")), space("s1", "Work", "t1"), tab("t1", "https://a.com"))
        val remote = object : SpacesRemote {
            override fun fetch(newerThan: Double) = serverRecords.map(::bso) to 10.0
            override fun upload(records: List<Pair<String, JSONObject>>) { uploads += records }
        }
        val creds = SyncCredentials("r", "k", ByteArray(64))
        val store = object : CredentialStore {
            override fun load() = creds
            override fun save(credentials: SyncCredentials?) {}
        }
        val repo = SpacesRepository(Files.createTempDirectory("zen").toFile(), store) { remote }
        repo.load()
        repo.sync()

        repo.edit(LocalEdit.OpenTab("phone", "s1", "https://phone.com", "Phone"))
        serverRecords = listOf(space("s1", "Work", "t1", "desk"), tab("desk", "https://desk.com"))
        repo.sync()

        val uploadedSpace = uploads.last().first { it.first == "s1" }.second
        val children = uploadedSpace.getJSONObject("data").getJSONArray("children").strings()
        assertEquals(listOf("t1", "desk", "phone"), children)
        assertEquals(listOf("t1", "desk", "phone"), repo.view.value.spaces()[0].tabs.map { it.id })
    }

    @Test
    fun `closing a tab uploads a sync tombstone`() = runBlocking {
        val uploads = mutableListOf<Pair<String, JSONObject>>()
        val remote = object : SpacesRemote {
            override fun fetch(newerThan: Double) =
                listOf(layout(listOf("s1")), space("s1", "Work", "t1"), tab("t1", "https://a.com")).map(::bso) to 1.0
            override fun upload(records: List<Pair<String, JSONObject>>) { uploads += records }
        }
        val store = object : CredentialStore {
            override fun load() = SyncCredentials("r", "k", ByteArray(64))
            override fun save(credentials: SyncCredentials?) {}
        }
        val repo = SpacesRepository(Files.createTempDirectory("zen").toFile(), store) { remote }
        repo.load()
        repo.sync()
        repo.edit(LocalEdit.CloseTab("t1"))
        repo.sync()
        val tombstone = uploads.first { it.first == "t1" }.second
        assertTrue(tombstone.getBoolean("deleted"))
    }

    @Test
    fun `moving a tab before a sibling reorders the space children`() {
        val (next, changes) = desktop.apply(listOf(LocalEdit.MoveItem("t3", "s1", "f1", pinned = null)))
        assertEquals(listOf("t3", "t2"), next.spaces()[1].tabs.map { it.id })
        assertEquals(setOf("s1"), changes.keys)
    }

    @Test
    fun `moving a tab into the pinned section pins it`() {
        val (next, changes) = desktop.apply(listOf(LocalEdit.MoveItem("t1", "s2", null, pinned = true)))
        assertTrue(next.tab("t1")!!.pinned)
        assertTrue(changes.containsKey("t1"))
    }

    @Test
    fun `moving a tab out of a folder updates both parents`() {
        val (next, changes) = desktop.apply(listOf(LocalEdit.MoveItem("t2", "s1", null, pinned = false)))
        assertEquals(listOf("f1", "t3", "t2"), next.spaces()[1].items.map { it.id })
        assertTrue((next.spaces()[1].items[0] as FolderItem).items.isEmpty())
        assertEquals(setOf("f1", "s1", "t2"), changes.keys)
    }

    @Test
    fun `move edits survive a json round trip`() {
        val edit = LocalEdit.MoveItem("a", "b", null, pinned = true)
        assertEquals(edit, LocalEdit.fromJson(edit.toJson()))
    }

    @Test
    fun `space theme and icons are read from desktop records`() {
        val theme = JSONObject().put("type", "gradient").put("opacity", 0.7).put(
            "gradientColors", JSONArray()
                .put(JSONObject().put("c", JSONArray(listOf(10, 20, 30))).put("isPrimary", false))
                .put(JSONObject().put("c", "#ff8800").put("isPrimary", true)),
        )
        val rec = space("s9", "Fun").apply {
            getJSONObject("data").put("theme", theme).put("icon", "chrome://browser/skin/zen-icons/selectable/airplane.svg")
        }
        val sp = stateOf(layout(listOf("s9")), rec).spaces().single()
        assertEquals(ZenIcon.Bundled("airplane"), sp.icon)
        assertEquals(listOf(0xFFFF8800L, 0xFF0A141EL), sp.theme!!.colors)
        assertEquals(0.7f, sp.theme!!.opacity)
    }

    @Test
    fun `emoji icons are kept and unknown urls are dropped`() {
        assertEquals(ZenIcon.Emoji("🏠"), ZenIcon.parse("🏠"))
        assertNull(ZenIcon.parse("moz-extension://x/icon.png"))
        assertNull(ZenIcon.parse(""))
    }

    @Test
    fun `only data favicons are exposed on tabs`() {
        val withData = tab("x", "https://a.com").apply { getJSONObject("data").put("icon", "data:image/png;base64,AA") }
        val withRemote = tab("y", "https://a.com").apply { getJSONObject("data").put("icon", "https://a.com/f.ico") }
        val state = stateOf(withData, withRemote)
        assertEquals("data:image/png;base64,AA", state.tab("x")!!.icon)
        assertNull(state.tab("y")!!.icon)
    }

    @Test
    fun `dragging a regular tab above the separator pins it before the next item`() {
        val edit = LocalEdit.placing("s1", "t3", pinnedIds = listOf("t3", "f1"), regularIds = listOf("t4"))
        assertEquals(LocalEdit.MoveItem("t3", "s1", "f1", pinned = true), edit)
        val (next, _) = desktop.apply(listOf(edit))
        assertEquals(listOf("t3", "f1"), next.spaces()[1].items.map { it.id })
        assertTrue(next.tab("t3")!!.pinned)
    }

    @Test
    fun `dragging to the end of the list anchors on nothing`() {
        assertEquals(LocalEdit.MoveItem("a", "s", null, pinned = false), LocalEdit.placing("s", "a", emptyList(), listOf("b", "a")))
    }

    @Test
    fun `a tab renamed on desktop shows its static label`() {
        val renamed = tab("r", "https://a.com").apply { getJSONObject("data").put("staticLabel", "Inbox") }
        val plain = tab("p", "https://a.com").apply { getJSONObject("data").put("staticLabel", JSONObject.NULL) }
        val state = stateOf(renamed, plain)
        assertEquals("Inbox", state.tab("r")!!.title)
        assertEquals("T-p", state.tab("p")!!.title)
    }
}
