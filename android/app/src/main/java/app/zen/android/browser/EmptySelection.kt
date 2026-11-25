package app.zen.android.browser

/** What to show once the search is dismissed while no page is open (at launch or on entering private mode). */
sealed interface Dismissal {
    data class Select(val tabId: String) : Dismissal
    data object LeavePrivate : Dismissal
    data object Blank : Dismissal
}

object EmptySelection {
    fun afterSearch(privateMode: Boolean, privateTabs: List<String>, spaceTabs: List<String>): Dismissal = when {
        privateMode -> privateTabs.lastOrNull()?.let(Dismissal::Select) ?: Dismissal.LeavePrivate
        else -> spaceTabs.firstOrNull()?.let(Dismissal::Select) ?: Dismissal.Blank
    }

    /** Entering private mode with nothing open goes straight to the search; otherwise the private sidebar lets the user pick. */
    fun privateEntryShowsSearch(privateTabs: List<String>): Boolean = privateTabs.isEmpty()
}
