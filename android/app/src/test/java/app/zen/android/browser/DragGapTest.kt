package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class DragGapTest {
    // Rows A B C D E, B (index 1) is dragged; without it the list is A C D E.
    private fun shifts(from: Int, target: Int, count: Int) = (0 until count).map { DragGap.slots(it, from, target) }

    @Test
    fun `dragging down lifts the rows passed over`() {
        assertEquals(listOf(0, -1, -1, 0), shifts(from = 1, target = 3, count = 4))
    }

    @Test
    fun `dragging up pushes the rows passed over down`() {
        // D (index 3) dragged to the top; without it: A B C E.
        assertEquals(listOf(1, 1, 1, 0), shifts(from = 3, target = 0, count = 4))
    }

    @Test
    fun `hovering its own slot moves nothing`() {
        assertEquals(listOf(0, 0, 0, 0), shifts(from = 1, target = 1, count = 4))
    }

    @Test
    fun `a tab dragged out of a folder opens a gap without leaving one`() {
        assertEquals(listOf(0, 0, 1, 1), shifts(from = -1, target = 2, count = 4))
    }
}
