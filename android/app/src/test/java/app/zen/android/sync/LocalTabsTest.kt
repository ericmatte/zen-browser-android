package app.zen.android.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTabsTest {
    private fun tab(id: String, pinned: Boolean = false) = TabItem(id, "https://$id.test/", id, pinned)
    private val folder = FolderItem("f", "Folder", listOf(tab("inFolder", pinned = true)))

    @Test
    fun `every space shows its synced pinned items and the same phone tabs`() {
        val work = Space("w", "Work", null, listOf(tab("p1", pinned = true), folder, tab("desktopTemp")))
        val home = Space("h", "Home", null, listOf(tab("p2", pinned = true), tab("otherTemp")))
        val local = listOf(tab("m1"), tab("m2"))
        val merged = LocalTabs.merge(listOf(work, home), local)
        assertEquals(listOf("p1", "f", "m1", "m2"), merged[0].items.map { it.id })
        assertEquals(listOf("p2", "m1", "m2"), merged[1].items.map { it.id })
        assertEquals(listOf("m1", "m2"), merged[1].regularTabs.map { it.id })
    }

    @Test
    fun `a phone tab that is also synced shows only once, as the synced one`() {
        val work = Space("w", "Work", null, listOf(tab("x", pinned = true)))
        val merged = LocalTabs.merge(listOf(work), listOf(tab("x"), tab("m")))
        assertEquals(listOf("x", "m"), merged[0].items.map { it.id })
        assertEquals(true, (merged[0].items[0] as TabItem).pinned)
    }

    @Test
    fun `a tab lands before the given one, or last`() {
        val list = listOf(tab("a"), tab("b"))
        assertEquals(listOf("a", "x", "b"), LocalTabs.insert(list, tab("x"), "b").map { it.id })
        assertEquals(listOf("a", "b", "x"), LocalTabs.insert(list, tab("x"), null).map { it.id })
        assertEquals(listOf("a", "b", "x"), LocalTabs.insert(list, tab("x"), "gone").map { it.id })
        assertEquals(listOf("b", "a"), LocalTabs.insert(list, tab("a"), null).map { it.id })
    }

    @Test
    fun `tabs the phone synced before are copied under new ids, desktop ones are not`() {
        val work = Space("w", "Work", null, listOf(tab("mine"), tab("desktop"), tab("pinnedMine", pinned = true)))
        var n = 0
        val copies = LocalTabs.migrate(listOf(work), setOf("mine", "pinnedMine"), newId = { "new${n++}" })
        assertEquals(listOf("new0"), copies.map { it.id })
        assertEquals("https://mine.test/", copies.single().url)
    }

    @Test
    fun `the list survives a round trip`() {
        val list = listOf(tab("a"), tab("b"))
        assertEquals(list, LocalTabs.decode(LocalTabs.encode(list)))
    }
}
