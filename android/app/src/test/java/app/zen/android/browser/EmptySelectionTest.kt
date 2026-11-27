package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptySelectionTest {
    @Test
    fun `dismissing the search reopens the most recently used tab`() {
        assertEquals(Dismissal.Select("r"), EmptySelection.afterSearch(false, emptyList(), listOf("a", "b"), recent = listOf("r", "a"), temporary = listOf("b")))
    }

    @Test
    fun `without a recent tab, dismissing the search opens the first temporary tab`() {
        assertEquals(Dismissal.Select("b"), EmptySelection.afterSearch(false, emptyList(), listOf("a", "b"), recent = emptyList(), temporary = listOf("b")))
    }

    @Test
    fun `a space with only pinned tabs opens its first tab`() {
        assertEquals(Dismissal.Select("a"), EmptySelection.afterSearch(false, emptyList(), listOf("a", "b"), recent = emptyList(), temporary = emptyList()))
    }

    @Test
    fun `dismissing the launch search with an empty space stays blank`() {
        assertEquals(Dismissal.Blank, EmptySelection.afterSearch(false, emptyList(), emptyList(), recent = emptyList(), temporary = emptyList()))
    }

    @Test
    fun `dismissing the private search reopens the latest private tab`() {
        assertEquals(Dismissal.Select("p2"), EmptySelection.afterSearch(true, listOf("p1", "p2"), listOf("a"), recent = listOf("a"), temporary = listOf("a")))
    }

    @Test
    fun `dismissing the private search with no private tab leaves private mode`() {
        assertEquals(Dismissal.LeavePrivate, EmptySelection.afterSearch(true, emptyList(), listOf("a"), recent = listOf("a"), temporary = listOf("a")))
    }

    @Test
    fun `private entry shows the search only when no private tab exists`() {
        assertTrue(EmptySelection.privateEntryShowsSearch(emptyList()))
        assertFalse(EmptySelection.privateEntryShowsSearch(listOf("p1")))
    }

    @Test
    fun `launch reopens the temporary tab used last`() {
        assertEquals("t2", EmptySelection.atLaunch(recent = listOf("pinned", "t2", "t1"), temporary = listOf("t1", "t2")))
    }

    @Test
    fun `launch opens the first temporary tab when none was used yet`() {
        assertEquals("t1", EmptySelection.atLaunch(recent = listOf("pinned"), temporary = listOf("t1", "t2")))
    }

    @Test
    fun `launch with no temporary tab opens the home search`() {
        assertEquals(null, EmptySelection.atLaunch(recent = listOf("pinned"), temporary = emptyList()))
    }

    @Test
    fun `returning to the app after leaving it from the back search reopens the last temporary tab`() {
        assertEquals("t2", EmptySelection.onReturn(searchFromBack = true, privateMode = false, recent = listOf("t2", "t1"), temporary = listOf("t1", "t2")))
    }

    @Test
    fun `returning to the app keeps a search the user opened`() {
        assertEquals(null, EmptySelection.onReturn(searchFromBack = false, privateMode = false, recent = listOf("t2"), temporary = listOf("t2")))
    }

    @Test
    fun `returning to the app keeps the back search when no temporary tab is left`() {
        assertEquals(null, EmptySelection.onReturn(searchFromBack = true, privateMode = false, recent = listOf("pinned"), temporary = emptyList()))
    }

    @Test
    fun `returning to the app in private mode keeps the back search`() {
        assertEquals(null, EmptySelection.onReturn(searchFromBack = true, privateMode = true, recent = listOf("t2"), temporary = listOf("t2")))
    }
}
