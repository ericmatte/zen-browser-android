package app.zen.android.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedHostsTest {
    @Test
    fun `a failed favicon is retried after the pause`() {
        val hosts = FailedHosts(retryAfterMillis = 1_000)
        hosts.fail("a.com", now = 0)
        assertFalse(hosts.shouldTry("a.com", now = 500))
        assertTrue(hosts.shouldTry("a.com", now = 1_000))
        assertTrue(hosts.shouldTry("b.com", now = 0))
    }
}
