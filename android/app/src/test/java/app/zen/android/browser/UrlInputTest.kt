package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlInputTest {
    @Test fun `bare host gets https`() = assertEquals("https://zen-browser.app", UrlInput.resolve("zen-browser.app"))
    @Test fun `host with port is a url`() = assertEquals("https://localhost:8080/x", UrlInput.resolve("localhost:8080/x"))
    @Test fun `explicit scheme is kept`() = assertEquals("http://a.com", UrlInput.resolve("http://a.com"))
    @Test fun `words become a search`() = assertEquals("https://duckduckgo.com/?q=zen+browser", UrlInput.resolve("zen browser"))
    @Test fun `single word becomes a search`() = assertEquals("https://duckduckgo.com/?q=zen", UrlInput.resolve("zen"))
    @Test fun `display strips scheme and path`() = assertEquals("github.com", UrlInput.display("https://www.github.com/zen"))
}
