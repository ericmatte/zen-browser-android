package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class DismissedSuggestionsTest {
    @Test
    fun `dismissed entries no longer appear`() {
        val dismissed = DismissedSuggestions.add(emptyList(), "page:https://a.com")
        assertEquals(listOf("b"), DismissedSuggestions.without(listOf("a", "b"), dismissed) { if (it == "a") "page:https://a.com" else it })
    }

    @Test
    fun `the list stays bounded and keeps the newest`() {
        val list = (1..250).fold(emptyList<String>()) { acc, i -> DismissedSuggestions.add(acc, "k$i") }
        assertEquals(DismissedSuggestions.MAX, list.size)
        assertEquals("k250", list.first())
    }
}
