package app.zen.android.browser

/** A pinned tab keeps the address it was pinned with; browsing away only changes what it shows, like desktop. */
object PinnedChange {
    fun changed(pinnedUrl: String, liveUrl: String?): Boolean =
        liveUrl != null && !UrlInput.isPlaceholder(liveUrl) && pinnedUrl.substringBefore('#') != liveUrl.substringBefore('#')
}
