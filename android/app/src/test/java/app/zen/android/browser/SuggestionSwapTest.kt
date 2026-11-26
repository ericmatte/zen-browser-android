package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionSwapTest {
    @Test
    fun `typing keeps the current list until the search engine answers`() {
        assertEquals(listOf("kotlin", "kotlin lang"), SuggestionSwap.whileWaiting(listOf("kotlin", "kotlin lang"), listOf("kotlin.org"), waitingForRemote = true))
    }

    @Test
    fun `without search suggestions the local matches show at once`() {
        assertEquals(listOf("kotlin.org"), SuggestionSwap.whileWaiting(listOf("kotlin"), listOf("kotlin.org"), waitingForRemote = false))
    }

    @Test
    fun `the first key shows the local matches instead of an empty panel`() {
        assertEquals(listOf("kotlin.org"), SuggestionSwap.whileWaiting(emptyList(), listOf("kotlin.org"), waitingForRemote = true))
    }
}
