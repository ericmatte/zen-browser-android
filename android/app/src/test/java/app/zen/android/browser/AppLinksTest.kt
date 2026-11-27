package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLinksTest {
    private val browsers = setOf("org.mozilla.firefox", "com.android.chrome", "app.zen.android")

    @Test
    fun `an app that claims the address is offered`() {
        assertEquals("com.google.android.youtube", AppLinks.pick(listOf("com.android.chrome", "com.google.android.youtube"), browsers, "app.zen.android"))
    }

    @Test
    fun `browsers alone are not offered`() {
        assertNull(AppLinks.pick(listOf("com.android.chrome", "org.mozilla.firefox"), browsers, "app.zen.android"))
    }

    @Test
    fun `this app is never offered`() {
        assertNull(AppLinks.pick(listOf("app.zen.android"), emptySet(), "app.zen.android"))
    }

    @Test
    fun `a tapped link leaves for its app`() {
        assertTrue(AppLinks.handsOff(userGesture = true, directNavigation = false, privateTab = false))
    }

    @Test
    fun `typed addresses, scripts and private tabs stay in the browser`() {
        assertFalse(AppLinks.handsOff(userGesture = true, directNavigation = true, privateTab = false))
        assertFalse(AppLinks.handsOff(userGesture = false, directNavigation = false, privateTab = false))
        assertFalse(AppLinks.handsOff(userGesture = true, directNavigation = false, privateTab = true))
    }
}
