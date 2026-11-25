package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test
    fun `only android tags carry a version`() {
        assertEquals("0.6.0", Updates.versionOf("android-v0.6.0"))
        assertNull(Updates.versionOf("1.17.2b"))
        assertNull(Updates.versionOf("android-vbeta"))
    }

    @Test
    fun `versions compare by number, not text`() {
        assertTrue(Updates.isNewer("0.10.0", "0.9.9"))
        assertTrue(Updates.isNewer("0.6.0", "0.5.9"))
        assertTrue(Updates.isNewer("1.0", "0.9.9"))
        assertFalse(Updates.isNewer("0.5.9", "0.5.9"))
        assertFalse(Updates.isNewer("0.5.8", "0.5.9"))
        assertFalse(Updates.isNewer("0.6", "0.6.0"))
    }

    @Test
    fun `a release from the api is read, prereleases are skipped`() {
        val release = Updates.parse("""{"tag_name":"android-v0.6.0","html_url":"https://github.com/x/y/releases/tag/android-v0.6.0","draft":false,"prerelease":false}""")
        assertEquals("0.6.0", release?.version)
        assertNull(Updates.parse("""{"tag_name":"android-v0.7.0","html_url":"u","prerelease":true}"""))
    }
}
