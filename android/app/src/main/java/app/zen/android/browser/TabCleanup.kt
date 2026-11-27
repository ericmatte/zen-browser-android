package app.zen.android.browser

/**
 * Unpinned tabs the phone opened and has not shown for [maxIdleMillis] are
 * closed, like desktop's ephemeral tabs. Tabs opened on another device are
 * left alone: closing them here would also close them on that device.
 */
object TabCleanupRule {
    fun expired(
        unpinned: Collection<String>,
        openedHere: Set<String>,
        lastSeen: Map<String, Long>,
        active: String,
        now: Long,
        maxIdleMillis: Long,
        // Seen times live in memory only; a tab not seen since the app started counts from the start.
        startedAt: Long,
    ): List<String> = unpinned.filter { id ->
        id != active && id in openedHere && now - (lastSeen[id] ?: startedAt) >= maxIdleMillis
    }
}
