package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpaceHighlightTest {
    private val offsets = mapOf(0 to 0, 1 to 36, 2 to 72)

    @Test
    fun `sits on the settled space`() {
        assertEquals(36f, SpaceHighlight.x(1f) { offsets[it] })
    }

    @Test
    fun `slides with the swipe between two spaces`() {
        assertEquals(18f, SpaceHighlight.x(0.5f) { offsets[it] })
    }

    @Test
    fun `stays on the last space when there is no next one`() {
        assertEquals(72f, SpaceHighlight.x(2f) { offsets[it] })
    }

    @Test
    fun `is hidden when the space icon is scrolled out of view`() {
        assertNull(SpaceHighlight.x(5f) { offsets[it] })
    }
}
