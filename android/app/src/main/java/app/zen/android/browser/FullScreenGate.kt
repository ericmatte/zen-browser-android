package app.zen.android.browser

/**
 * Gecko lets pages request fullscreen without a gesture (needed for
 * fullscreen-on-rotate), so the app enforces the gesture itself: a page may
 * cover the browser only right after the user touched it, or right after the
 * phone turned while that page plays a video (fullscreen-on-rotate).
 */
object FullScreenGate {
    const val WINDOW_MILLIS = 1500L

    fun allowed(now: Long, lastTouch: Long, lastRotation: Long, mediaPlaying: Boolean): Boolean =
        now - lastTouch <= WINDOW_MILLIS || (mediaPlaying && now - lastRotation <= WINDOW_MILLIS)
}
