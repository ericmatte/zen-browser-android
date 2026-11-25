package app.zen.android.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenGateTest {
    @Test
    fun `a page may go fullscreen right after a touch`() {
        assertTrue(FullScreenGate.allowed(now = 10_000, lastTouch = 9_500, lastRotation = 0))
    }

    @Test
    fun `a page may go fullscreen right after the phone turns sideways`() {
        assertTrue(FullScreenGate.allowed(now = 10_000, lastTouch = 0, lastRotation = 9_000))
    }

    @Test
    fun `a page cannot go fullscreen on its own`() {
        assertFalse(FullScreenGate.allowed(now = 10_000, lastTouch = 7_000, lastRotation = 2_000))
    }
}
