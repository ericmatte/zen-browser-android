package app.zen.android.browser

import org.mozilla.geckoview.PanZoomController

/**
 * Pulling down reloads only when Gecko itself would let the page overscroll
 * at the top: the touch went to the page's own scroller (not a map or a
 * script that grabs it), nothing above is left to scroll, and the page allows
 * vertical overscroll. This mirrors Firefox for Android's rule.
 */
object PullToRefresh {
    fun allowed(handled: Int, scrollable: Int, overscroll: Int): Boolean =
        handled == PanZoomController.INPUT_RESULT_HANDLED &&
            scrollable and PanZoomController.SCROLLABLE_FLAG_TOP == 0 &&
            overscroll and PanZoomController.OVERSCROLL_FLAG_VERTICAL != 0

    /** The finger travels further than the indicator, which slows down past the trigger point. */
    fun resisted(drag: Float, trigger: Float): Float =
        if (drag <= trigger) drag * 0.5f else trigger * 0.5f + (drag - trigger) * 0.2f
}
