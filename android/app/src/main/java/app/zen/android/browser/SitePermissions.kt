package app.zen.android.browser

import org.mozilla.geckoview.GeckoSession.PermissionDelegate as P

data class SitePermissionRow(val kind: Int, val label: String, val allowed: Boolean)

object SitePermissions {
    fun label(kind: Int): String? = when (kind) {
        P.PERMISSION_GEOLOCATION -> "Location"
        P.PERMISSION_DESKTOP_NOTIFICATION -> "Notifications"
        P.PERMISSION_PERSISTENT_STORAGE -> "Store data"
        P.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "Protected content"
        P.PERMISSION_STORAGE_ACCESS -> "Cross-site cookies"
        P.PERMISSION_AUTOPLAY_AUDIBLE -> "Autoplay with sound"
        P.PERMISSION_AUTOPLAY_INAUDIBLE -> "Autoplay muted"
        else -> null
    }

    /** Only decisions the user made show: "ask every time" is the absence of a decision, and unknown kinds mean nothing to them. */
    fun rows(stored: List<Pair<Int, Int>>): List<SitePermissionRow> = stored
        .filter { (_, value) -> value != P.ContentPermission.VALUE_PROMPT }
        .mapNotNull { (kind, value) -> label(kind)?.let { SitePermissionRow(kind, it, value == P.ContentPermission.VALUE_ALLOW) } }
        .distinctBy { it.kind }
        .sortedBy { it.label }
}
