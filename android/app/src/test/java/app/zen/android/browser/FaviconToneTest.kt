package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaviconToneTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val clear = 0x00FFFFFF

    @Test
    fun `transparent pixels do not count`() {
        assertEquals(0f, FaviconTone.luminance(intArrayOf(black, clear, clear))!!, 0.001f)
    }

    @Test
    fun `a fully transparent icon has no luminance`() {
        assertNull(FaviconTone.luminance(intArrayOf(clear, clear)))
    }

    @Test
    fun `a black icon blends into a dark surface but not a light one`() {
        val l = FaviconTone.luminance(intArrayOf(black))!!
        assertTrue(FaviconTone.blendsIn(l, darkSurface = true))
        assertFalse(FaviconTone.blendsIn(l, darkSurface = false))
    }

    @Test
    fun `a white icon blends into a light surface but not a dark one`() {
        val l = FaviconTone.luminance(intArrayOf(white))!!
        assertTrue(FaviconTone.blendsIn(l, darkSurface = false))
        assertFalse(FaviconTone.blendsIn(l, darkSurface = true))
    }

    @Test
    fun `a colourful icon never needs help`() {
        val l = FaviconTone.luminance(intArrayOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt()))!!
        assertFalse(FaviconTone.blendsIn(l, darkSurface = true))
        assertFalse(FaviconTone.blendsIn(l, darkSurface = false))
    }
}
