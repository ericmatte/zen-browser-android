package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabSnapshotTest {
    @Test
    fun `history and scroll survive a round trip`() {
        val snapshot = TabSnapshot("""{"history":{"index":1}}""", 1840)
        assertEquals(snapshot, TabSnapshot.decode(snapshot.encode()))
    }

    @Test
    fun `older files holding only Gecko's state still restore`() {
        assertEquals(TabSnapshot("""{"history":{"index":1}}""", 0), TabSnapshot.decode("""{"history":{"index":1}}"""))
    }

    @Test
    fun `a scroll offset alone is kept`() {
        assertEquals(TabSnapshot(null, 300), TabSnapshot.decode(TabSnapshot(null, 300).encode()))
    }

    @Test
    fun `garbage restores nothing`() {
        assertNull(TabSnapshot.decode("not json"))
    }
}
