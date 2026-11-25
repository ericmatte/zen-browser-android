package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentSearchesTest {
    @Test
    fun `newest search comes first and repeats move to the top`() {
        val list = RecentSearches.add(RecentSearches.add(listOf("kotlin"), "zen browser"), "Kotlin")
        assertEquals(listOf("Kotlin", "zen browser"), list)
    }

    @Test
    fun `keeps at most six searches`() {
        val list = (1..8).fold(emptyList<String>()) { acc, i -> RecentSearches.add(acc, "q$i") }
        assertEquals(listOf("q8", "q7", "q6", "q5", "q4", "q3"), list)
    }

    @Test
    fun `blank terms are ignored and encoding round-trips`() {
        val list = RecentSearches.add(listOf("a b"), "  ")
        assertEquals(listOf("a b"), RecentSearches.decode(RecentSearches.encode(list)))
    }

    @Test
    fun `addresses are not searches`() {
        assertTrue(UrlInput.isSearch("weather today"))
        assertFalse(UrlInput.isSearch("example.com"))
        assertFalse(UrlInput.isSearch("https://zen-browser.app"))
    }
}
