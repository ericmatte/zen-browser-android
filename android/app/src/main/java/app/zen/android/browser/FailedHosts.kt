package app.zen.android.browser

/** A favicon that failed (offline, slow site) is retried after a pause instead of never again in this run. */
class FailedHosts(private val retryAfterMillis: Long = 60_000) {
    private val failedAt = mutableMapOf<String, Long>()

    @Synchronized
    fun shouldTry(host: String, now: Long): Boolean = failedAt[host]?.let { now - it >= retryAfterMillis } ?: true

    @Synchronized
    fun fail(host: String, now: Long) {
        failedAt[host] = now
    }

    @Synchronized
    fun succeed(host: String) {
        failedAt.remove(host)
    }
}
