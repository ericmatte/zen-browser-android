package app.zen.android.browser

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TabStateStoreTest {
    private val dir: File = Files.createTempDirectory("tabstate").toFile()
    private val now = java.util.concurrent.Executor { it.run() }

    @Test
    fun `a saved state survives a new store, as after a restart`() {
        TabStateStore(dir, now).save("tab-1", """{"history":1}""")
        assertEquals("""{"history":1}""", TabStateStore(dir, now).load("tab-1"))
    }

    @Test
    fun `only the newest state of a burst is written`() {
        val pending = mutableListOf<Runnable>()
        val store = TabStateStore(dir, { pending += it })
        store.save("tab-1", "a")
        store.save("tab-1", "b")
        assertEquals(1, pending.size)
        pending.forEach { it.run() }
        assertEquals("b", TabStateStore(dir, now).load("tab-1"))
    }

    @Test
    fun `private tabs are never written`() {
        TabStateStore(dir, now).save(PRIVATE_PREFIX + "x", "secret")
        assertNull(TabStateStore(dir, now).load(PRIVATE_PREFIX + "x"))
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }

    @Test
    fun `closing a tab forgets its state`() {
        val store = TabStateStore(dir, now)
        store.save("tab-1", "a")
        store.delete("tab-1")
        assertNull(TabStateStore(dir, now).load("tab-1"))
    }

    @Test
    fun `odd ids never become paths`() {
        TabStateStore(dir, now).save("../escape", "x")
        assertFalse(File(dir.parentFile, "escape.json").exists())
    }
}
