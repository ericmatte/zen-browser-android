package app.zen.android.browser

enum class SwipeOutcome { COMMIT_START, COMMIT_END, REVEAL, CLOSE }

/**
 * Where a swiped row lands when the finger lifts. A row that was already
 * open closes as soon as it is pushed back a little, so it never stops
 * half-way, whatever the finger's start point or swipe length.
 */
object SwipeSettle {
    private const val COMMIT_SHARE = 0.35f
    private const val FLING = 1500f

    fun decide(
        offset: Float,
        velocity: Float,
        width: Float,
        reveal: Float,
        wasRevealed: Boolean,
        canReveal: Boolean,
        canCommitStart: Boolean,
        canCommitEnd: Boolean,
    ): SwipeOutcome {
        val commit = width * COMMIT_SHARE
        if (offset > 0f) {
            val committed = offset > commit || velocity > FLING
            return if (committed && canCommitStart && !wasRevealed) SwipeOutcome.COMMIT_START else SwipeOutcome.CLOSE
        }
        if (canCommitEnd && (offset < -commit || velocity < -FLING)) return SwipeOutcome.COMMIT_END
        if (!canReveal || offset == 0f) return SwipeOutcome.CLOSE
        val stays = if (wasRevealed) offset <= -reveal * 0.8f && velocity <= 300f else offset < -reveal / 2 || velocity < -800f
        return if (stays) SwipeOutcome.REVEAL else SwipeOutcome.CLOSE
    }
}
