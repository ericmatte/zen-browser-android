package app.zen.android.browser

/** What to show once the search is dismissed while no page is open (at launch, after back, or on entering private mode). */
sealed interface Dismissal {
    data class Select(val tabId: String) : Dismissal
    data object LeavePrivate : Dismissal
    data object Blank : Dismissal
}

object EmptySelection {
    /** [recent] is most recent first; [temporary] are the space's unpinned tabs in sidebar order. */
    fun afterSearch(privateMode: Boolean, privateTabs: List<String>, spaceTabs: List<String>, recent: List<String>, temporary: List<String>): Dismissal = when {
        privateMode -> privateTabs.lastOrNull()?.let(Dismissal::Select) ?: Dismissal.LeavePrivate
        else -> (recent.firstOrNull() ?: temporary.firstOrNull() ?: spaceTabs.firstOrNull())?.let(Dismissal::Select) ?: Dismissal.Blank
    }

    /** At launch: the temporary tab used last (else the first one) comes back; with none, the home search opens. */
    fun atLaunch(recent: List<String>, temporary: List<String>): String? =
        recent.firstOrNull { it in temporary } ?: temporary.firstOrNull()

    /**
     * Back on a page with no history shows the search, and a second back leaves the app with that search still
     * open. Coming back must not land on that search when a temporary tab can be shown instead.
     */
    fun onReturn(searchFromBack: Boolean, privateMode: Boolean, recent: List<String>, temporary: List<String>): String? =
        if (searchFromBack && !privateMode) atLaunch(recent, temporary) else null

    /** Entering private mode with nothing open goes straight to the search; otherwise the private sidebar lets the user pick. */
    fun privateEntryShowsSearch(privateTabs: List<String>): Boolean = privateTabs.isEmpty()
}
