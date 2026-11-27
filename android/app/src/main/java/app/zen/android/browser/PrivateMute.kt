package app.zen.android.browser

/**
 * Entering an empty private session silences media, so the first private page never plays out loud.
 * Leaving gives the volume back, unless the user set one meanwhile.
 */
object PrivateMute {
    fun shouldMute(privateTabCount: Int, volume: Int): Boolean = privateTabCount == 0 && volume > 0

    fun restoreTo(saved: Int?, current: Int): Int? = saved?.takeIf { current == 0 }
}
