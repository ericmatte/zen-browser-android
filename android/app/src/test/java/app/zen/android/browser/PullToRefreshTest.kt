package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.PanZoomController

class PullToRefreshTest {
    private val handled = PanZoomController.INPUT_RESULT_HANDLED
    private val vertical = PanZoomController.OVERSCROLL_FLAG_VERTICAL

    @Test
    fun `a page at its top that allows overscroll can be pulled`() {
        assertTrue(PullToRefresh.allowed(handled, PanZoomController.SCROLLABLE_FLAG_BOTTOM, vertical))
    }

    @Test
    fun `a page that can still scroll up cannot be pulled`() {
        assertFalse(PullToRefresh.allowed(handled, PanZoomController.SCROLLABLE_FLAG_TOP, vertical))
    }

    @Test
    fun `content that handles the touch itself cannot be pulled`() {
        assertFalse(PullToRefresh.allowed(PanZoomController.INPUT_RESULT_HANDLED_CONTENT, PanZoomController.SCROLLABLE_FLAG_NONE, vertical))
    }

    @Test
    fun `a page that forbids vertical overscroll cannot be pulled`() {
        assertFalse(PullToRefresh.allowed(handled, PanZoomController.SCROLLABLE_FLAG_NONE, PanZoomController.OVERSCROLL_FLAG_NONE))
    }

    @Test
    fun `the indicator slows down past the trigger`() {
        assertEquals(40f, PullToRefresh.resisted(80f, 160f), 0.001f)
        assertEquals(84f, PullToRefresh.resisted(180f, 160f), 0.001f)
    }
}
