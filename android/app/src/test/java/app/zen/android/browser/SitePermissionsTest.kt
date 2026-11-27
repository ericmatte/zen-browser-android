package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.geckoview.GeckoSession.PermissionDelegate as P

class SitePermissionsTest {
    private val allow = P.ContentPermission.VALUE_ALLOW
    private val deny = P.ContentPermission.VALUE_DENY
    private val prompt = P.ContentPermission.VALUE_PROMPT

    @Test
    fun `decisions show sorted by name`() {
        val rows = SitePermissions.rows(listOf(P.PERMISSION_GEOLOCATION to allow, P.PERMISSION_DESKTOP_NOTIFICATION to deny))
        assertEquals(
            listOf(SitePermissionRow(P.PERMISSION_GEOLOCATION, "Location", true), SitePermissionRow(P.PERMISSION_DESKTOP_NOTIFICATION, "Notifications", false)),
            rows,
        )
    }

    @Test
    fun `ask-every-time and unknown kinds are hidden`() {
        assertEquals(emptyList<SitePermissionRow>(), SitePermissions.rows(listOf(P.PERMISSION_GEOLOCATION to prompt, 999 to allow)))
    }
}
