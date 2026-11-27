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

class MediaDecisionsTest {
    private val store = mutableMapOf<String, Boolean>()
    private val decisions = MediaDecisions({ store[it] }) { k, v -> if (v == null) store.remove(k) else store[k] = v }

    @Test
    fun `an answer is remembered for the site, whichever it was`() {
        decisions.remember("a.com", listOf(CAMERA_KIND), false)
        assertEquals(false, decisions.decided("a.com", listOf(CAMERA_KIND)))
        decisions.remember("b.com", listOf(CAMERA_KIND, MICROPHONE_KIND), true)
        assertEquals(true, decisions.decided("b.com", listOf(CAMERA_KIND, MICROPHONE_KIND)))
    }

    @Test
    fun `a device never answered still asks`() {
        decisions.remember("a.com", listOf(CAMERA_KIND), true)
        assertEquals(null, decisions.decided("a.com", listOf(CAMERA_KIND, MICROPHONE_KIND)))
        assertEquals(listOf(SitePermissionRow(CAMERA_KIND, "Camera", true)), decisions.rows("a.com"))
    }
}
