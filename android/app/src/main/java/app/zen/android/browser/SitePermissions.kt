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

const val CAMERA_KIND = -1
const val MICROPHONE_KIND = -2

/** Gecko never stores camera and microphone answers, so the app keeps them per site. */
class MediaDecisions(private val read: (String) -> Boolean?, private val write: (String, Boolean?) -> Unit) {
    private fun key(host: String, kind: Int) = "$host|$kind"

    /** Allowed only when every device asked for was allowed; one block blocks; anything unanswered asks. */
    fun decided(host: String, kinds: List<Int>): Boolean? {
        val answers = kinds.map { read(key(host, it)) }
        return when {
            answers.any { it == false } -> false
            answers.isNotEmpty() && answers.all { it == true } -> true
            else -> null
        }
    }

    fun remember(host: String, kinds: List<Int>, allowed: Boolean?) = kinds.forEach { write(key(host, it), allowed) }

    fun rows(host: String): List<SitePermissionRow> = listOf(CAMERA_KIND to "Camera", MICROPHONE_KIND to "Microphone")
        .mapNotNull { (kind, label) -> read(key(host, kind))?.let { SitePermissionRow(kind, label, it) } }
}
