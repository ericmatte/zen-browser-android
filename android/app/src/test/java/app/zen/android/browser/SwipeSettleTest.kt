package app.zen.android.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeSettleTest {
    private fun settle(offset: Float, velocity: Float = 0f, wasRevealed: Boolean = false, canReveal: Boolean = true, start: Boolean = true, end: Boolean = false) =
        SwipeSettle.decide(offset, velocity, width = 1000f, reveal = 200f, wasRevealed = wasRevealed, canReveal = canReveal, canCommitStart = start, canCommitEnd = end)

    @Test
    fun `a half swipe to the left uncovers the button`() {
        assertEquals(SwipeOutcome.REVEAL, settle(-150f))
    }

    @Test
    fun `a short nudge to the left springs back`() {
        assertEquals(SwipeOutcome.CLOSE, settle(-60f))
    }

    @Test
    fun `pushing an open row back a little closes it fully`() {
        assertEquals(SwipeOutcome.CLOSE, settle(-120f, wasRevealed = true))
        assertEquals(SwipeOutcome.CLOSE, settle(-190f, velocity = 600f, wasRevealed = true))
    }

    @Test
    fun `an open row pushed past its start closes instead of pinning`() {
        assertEquals(SwipeOutcome.CLOSE, settle(500f, wasRevealed = true))
    }

    @Test
    fun `an open row left alone stays open`() {
        assertEquals(SwipeOutcome.REVEAL, settle(-200f, wasRevealed = true))
    }

    @Test
    fun `a long swipe to the right commits the start action`() {
        assertEquals(SwipeOutcome.COMMIT_START, settle(400f))
    }

    @Test
    fun `rows without a button commit the end action on a long swipe`() {
        assertEquals(SwipeOutcome.COMMIT_END, settle(-400f, canReveal = false, end = true))
        assertEquals(SwipeOutcome.CLOSE, settle(-150f, canReveal = false, end = true))
    }
}
