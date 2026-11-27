package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class TabCleanupTest {
    private val hour = 3_600_000L

    @Test
    fun `only idle unpinned tabs opened on the phone are closed`() {
        val expired = TabCleanupRule.expired(
            unpinned = listOf("old", "fresh", "desktop", "active"),
            openedHere = setOf("old", "fresh", "active"),
            lastSeen = mapOf("old" to 0L, "fresh" to 11 * hour, "active" to 0L),
            active = "active",
            now = 12 * hour,
            maxIdleMillis = 12 * hour,
            startedAt = 0L,
        )
        assertEquals(listOf("old"), expired)
    }

    @Test
    fun `a tab not seen since a recent restart is kept`() {
        val expired = TabCleanupRule.expired(
            unpinned = listOf("t"),
            openedHere = setOf("t"),
            lastSeen = emptyMap(),
            active = "other",
            now = 100 * hour,
            maxIdleMillis = 12 * hour,
            startedAt = 99 * hour,
        )
        assertEquals(emptyList<String>(), expired)
    }
}
