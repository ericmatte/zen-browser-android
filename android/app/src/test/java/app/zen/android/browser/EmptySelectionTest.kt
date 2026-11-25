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
}
