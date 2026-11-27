package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateMuteTest {
    @Test
    fun `an empty private session starts muted`() {
        assertTrue(PrivateMute.shouldMute(privateTabCount = 0, volume = 7))
        assertFalse(PrivateMute.shouldMute(privateTabCount = 2, volume = 7))
        assertFalse(PrivateMute.shouldMute(privateTabCount = 0, volume = 0))
    }

    @Test
    fun `leaving restores the volume unless the user changed it`() {
        assertEquals(7, PrivateMute.restoreTo(saved = 7, current = 0))
        assertNull(PrivateMute.restoreTo(saved = 7, current = 4))
        assertNull(PrivateMute.restoreTo(saved = null, current = 0))
    }
}
