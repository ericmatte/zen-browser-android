package app.zen.android.browser

import android.content.Context
import android.view.MotionEvent
import org.mozilla.geckoview.GeckoView

/** A GeckoView that learns, at each touch-down, whether the page would accept a pull-to-refresh. */
class PullAwareGeckoView(context: Context) : GeckoView(context) {
    @Volatile var canPull = false
        private set

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)
        canPull = false
        requestFocus()
        val session = session ?: return false
        session.panZoomController.onTouchEventForDetailResult(event).accept { detail ->
            canPull = detail != null && PullToRefresh.allowed(detail.handledResult(), detail.scrollableDirections(), detail.overscrollDirections())
        }
        return true
    }
}
