package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class IncomingLinkTest {
    private val google = SearchEngine.GOOGLE

    @Test
    fun `web links from other apps open as is`() {
        assertEquals("https://zen-browser.app/", UrlInput.resolveIncoming("https://zen-browser.app/", google))
        assertEquals("http://example.com/a", UrlInput.resolveIncoming("Look: http://example.com/a !", google))
        assertEquals("https://example.com", UrlInput.resolveIncoming("example.com", google))
    }

    @Test
    fun `other schemes from other apps are searched, never opened`() {
        listOf("file:///data/data/app.zen.android/files/fxa.json", "javascript:alert(1)", "data:text/html,<h1>x</h1>", "about:config", "intent://x#Intent;end")
            .forEach { assertEquals(it, google.search(it), UrlInput.resolveIncoming(it, google)) }
    }

    @Test
    fun `a web link wrapped in another scheme opens only as the plain web page`() {
        assertEquals("https://a.b", UrlInput.resolveIncoming("view-source:https://a.b", google))
    }

    @Test
    fun `plain words from other apps are searched`() {
        assertEquals(google.search("zen browser"), UrlInput.resolveIncoming("zen browser", google))
    }
}
