package app.zen.android.browser

import kotlin.math.floor

object SpaceHighlight {
    /** Where the space highlight sits for a pager [position]; [offsetOf] is null for icons scrolled out of view. */
    fun x(position: Float, offsetOf: (Int) -> Int?): Float? {
        val from = floor(position).toInt()
        val start = offsetOf(from) ?: return null
        val next = offsetOf(from + 1) ?: return start.toFloat()
        return start + (next - start) * (position - from)
    }
}
