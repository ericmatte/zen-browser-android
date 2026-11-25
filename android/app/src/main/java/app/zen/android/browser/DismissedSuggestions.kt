package app.zen.android.browser

/** Suggestions the user swiped away; they are kept out of every later suggestion list. */
object DismissedSuggestions {
    const val MAX = 200

    fun add(list: List<String>, key: String): List<String> = (listOf(key) + list.filterNot { it == key }).take(MAX)

    fun <T> without(items: List<T>, dismissed: Collection<String>, key: (T) -> String): List<T> {
        if (dismissed.isEmpty()) return items
        val set = dismissed.toSet()
        return items.filterNot { key(it) in set }
    }
}
