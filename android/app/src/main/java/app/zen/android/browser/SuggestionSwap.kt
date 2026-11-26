package app.zen.android.browser

/**
 * While the user types, the list on screen only changes once the new one is
 * complete: swapping in the local matches first and the search engine's a
 * moment later made the panel shrink and grow on every key.
 */
object SuggestionSwap {
    /** Wait this long at most for the search engine before showing the local matches alone. */
    const val REMOTE_PATIENCE_MILLIS = 700L

    /** What to show right after a key press, before the search engine answers. */
    fun <T> whileWaiting(shown: List<T>, local: List<T>, waitingForRemote: Boolean): List<T> =
        if (waitingForRemote && shown.isNotEmpty()) shown else local
}
