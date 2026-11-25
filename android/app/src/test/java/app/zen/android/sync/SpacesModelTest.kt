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
}
