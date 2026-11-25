package app.zen.android.ui

import app.zen.android.sync.SpaceTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZenGradientTest {
    @Test
    fun `oklab round trip keeps the colour`() {
        val c = doubleArrayOf(247.0, 111.0, 83.0)
        assertArrayEquals(c, ZenGradient.oklabToRgb(ZenGradient.rgbToOklab(c)), 0.01)
    }

    @Test
    fun `mixBase hits both ends`() {
        val a = doubleArrayOf(255.0, 0.0, 0.0)
        val b = doubleArrayOf(0.0, 0.0, 255.0)
        assertArrayEquals(a, ZenGradient.mixBase(a, b, 0.0), 0.01)
        assertArrayEquals(b, ZenGradient.mixBase(a, b, 1.0), 0.01)
    }

    @Test
    fun `layer alpha follows the desktop opacity slider curve`() {
        assertEquals(0.2, ZenGradient.layerAlpha(0.3), 1e-9)
        assertEquals(0.88, ZenGradient.layerAlpha(0.8), 1e-9)
        assertEquals(0.2 + 0.68 * 0.25, ZenGradient.layerAlpha(0.55), 1e-9)
    }

    @Test
    fun `single colour is composited over the dark underlay`() {
        val spec = ZenGradient.spec(SpaceTheme(listOf(0xFFFF0000L), 0.8f), dark = true)
        val r = Math.round(255 * 0.88 + 19 * 0.12)
        val gb = Math.round(19 * 0.12)
        assertEquals(0xFF000000L or (r shl 16) or (gb shl 8) or gb, spec.stops.first().second)
        assertNull(spec.overlay)
    }

    @Test
    fun `two colours produce the twelve desktop stops`() {
        val spec = ZenGradient.spec(SpaceTheme(listOf(0xFFFF0000L, 0xFF0000FFL), 0.5f), dark = false)
        assertEquals(12, spec.stops.size)
        assertEquals(spec.stops[9].second, spec.stops[11].second)
    }
}
