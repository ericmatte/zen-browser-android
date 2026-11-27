package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class SiteUsageTest {
    @Test
    fun `sizes read like the system's storage screen`() {
        assertEquals("0 B", SiteUsage.label(0))
        assertEquals("13 kB", SiteUsage.label(12_600))
        assertEquals("12.3 MB", SiteUsage.label(12_345_678))
    }
}
