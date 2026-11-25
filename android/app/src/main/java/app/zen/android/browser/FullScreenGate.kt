package app.zen.android.browser

/**
 * Gecko lets pages request fullscreen without a gesture (needed for
 * fullscreen-on-rotate), so the app enforces the gesture itself: a page may
 * cover the browser only right after the user touched it or turned the phone.
 */
object FullScreenGate {
    const val WINDOW_MILLIS = 1500L

    fun allowed(now: Long, lastTouch: Long, lastRotation: Long): Boolean =
        now - lastTouch <= WINDOW_MILLIS || now - lastRotation <= WINDOW_MILLIS
}
