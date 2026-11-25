package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageColorTest {
    private val white = 0xFFFFFFFF.toInt()
    private val blue = 0xFF1A73E8.toInt()

    @Test
    fun `most frequent colour wins over a small logo`() {
        val row = IntArray(100) { if (it in 10..20) blue else white }
        assertEquals(white, PageColor.dominant(row))
    }

    @Test
    fun `near identical shades are grouped together`() {
        val row = IntArray(100) { if (it % 2 == 0) 0xFF1A73E8.toInt() else 0xFF1B74E9.toInt() }
        assertEquals(blue, PageColor.dominant(row))
    }

    @Test
    fun `noisy rows have no dominant colour`() {
        val row = IntArray(100) { 0xFF000000.toInt() or (it * 40_000) }
        assertNull(PageColor.dominant(row))
    }
}
