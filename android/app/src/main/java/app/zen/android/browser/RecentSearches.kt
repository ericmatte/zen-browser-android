package app.zen.android.browser

/** Terms typed into the search, newest first, shown before anything is typed (like desktop's recent searches). */
object RecentSearches {
    const val MAX = 6

    fun add(list: List<String>, term: String): List<String> {
        val clean = term.trim()
        if (clean.isEmpty()) return list
        return (listOf(clean) + list.filterNot { it.equals(clean, ignoreCase = true) }).take(MAX)
    }

    fun encode(list: List<String>): String = list.joinToString("\n")

    fun decode(raw: String?): List<String> = raw?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
}
