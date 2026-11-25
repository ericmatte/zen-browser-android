package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class HistoryTest {
    private val day = 86_400_000L

    @Test
    fun `nothing is suggested before typing`() {
        val h = History(null)
        h.record("https://claude.ai/", "Claude")
        assertTrue(h.search("  ").isEmpty())
    }

    @Test
    fun `every word must match the title or url`() {
        val h = History(null)
        h.record("https://tcrf.net/GameCube", "Nintendo GameCube Preview Disc")
        h.record("https://claude.ai/", "Claude")
        assertEquals(listOf("https://tcrf.net/GameCube"), h.search("gamecube preview").map { it.url })
    }

    @Test
    fun `host prefix matches rank first, then frequent recent pages`() {
        val h = History(null)
        val now = 100 * day
        repeat(5) { h.record("https://example.com/claude-review", "Claude review", now - 30 * day) }
        h.record("https://claude.ai/", "Claude", now - day)
        h.record("https://news.com/claude", "News", now)
        assertEquals("https://claude.ai/", h.search("claude", now = now).first().url)
        assertEquals("https://example.com/claude-review", h.search("claude", now = now)[1].url)
    }

    @Test
    fun `history survives a restart and drops the oldest past the limit`() {
        val file = Files.createTempFile("history", ".json").toFile()
        val h = History(file, limit = 2)
        h.record("https://a.com/", "A")
        h.record("https://b.com/", "B")
        h.record("https://c.com/", "C")
        h.save()
        val reloaded = History(file)
        assertEquals(listOf("https://b.com/", "https://c.com/"), reloaded.search("com", max = 5, now = 0).map { it.url }.sorted())
    }

    @Test
    fun `non web pages are not recorded`() {
        val h = History(null)
        h.record("about:blank", "")
        assertTrue(h.search("blank").isEmpty())
    }
}
