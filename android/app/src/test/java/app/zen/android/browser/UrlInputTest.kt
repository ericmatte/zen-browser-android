package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class UrlInputTest {
    @Test fun `bare host gets https`() = assertEquals("https://zen-browser.app", UrlInput.resolve("zen-browser.app"))
    @Test fun `host with port is a url`() = assertEquals("https://localhost:8080/x", UrlInput.resolve("localhost:8080/x"))
    @Test fun `explicit scheme is kept`() = assertEquals("http://a.com", UrlInput.resolve("http://a.com"))
    @Test fun `words become a google search by default`() = assertEquals("https://www.google.com/search?q=zen+browser", UrlInput.resolve("zen browser"))
    @Test fun `single word becomes a search`() = assertEquals("https://www.google.com/search?q=zen", UrlInput.resolve("zen"))
    @Test fun `chosen engine is used`() = assertEquals("https://duckduckgo.com/?q=zen", UrlInput.resolve("zen", SearchEngine.DUCKDUCKGO))
    @Test fun `empty input opens the engine home`() = assertEquals("https://www.google.com/", UrlInput.resolve("  "))
    @Test fun `display strips scheme and path`() = assertEquals("github.com", UrlInput.display("https://www.github.com/zen"))
    @Test fun `suggestions are read from the opensearch shape`() =
        assertEquals(listOf("zen browser", "zen mode"), UrlInput.parseSuggestions("""["zen",["zen browser","zen mode",""]]"""))
    @Test fun `malformed suggestions are ignored`() = assertEquals(emptyList<String>(), UrlInput.parseSuggestions("<html>"))

    @Test
    fun `gecko's blank placeholder is never taken for a tab's address`() {
        assertTrue(UrlInput.isPlaceholder("about:blank"))
        assertTrue(UrlInput.isPlaceholder(""))
        assertFalse(UrlInput.isPlaceholder("https://news.ycombinator.com/"))
    }
    @Test fun `a decimal number is searched`() = assertEquals(SearchEngine.GOOGLE.search("3.14"), UrlInput.resolve("3.14"))
    @Test fun `an ip address is a url`() = assertEquals("https://192.168.1.1:8080", UrlInput.resolve("192.168.1.1:8080"))
    @Test fun `a multi-part domain is a url`() = assertEquals("https://news.bbc.co.uk", UrlInput.resolve("news.bbc.co.uk"))
}
