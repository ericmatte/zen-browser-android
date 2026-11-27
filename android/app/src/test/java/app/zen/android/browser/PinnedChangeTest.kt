package app.zen.android.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedChangeTest {
    @Test
    fun `browsing away from the pinned address is a change`() {
        assertTrue(PinnedChange.changed("https://mail.google.com/", "https://mail.google.com/inbox/123"))
    }

    @Test
    fun `the same page or only a different anchor is not`() {
        assertFalse(PinnedChange.changed("https://a.com/doc", "https://a.com/doc#part2"))
        assertFalse(PinnedChange.changed("https://a.com/doc", null))
    }
}
