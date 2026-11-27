package app.zen.android.browser

/**
 * While a row is dragged, the other rows slide apart to open a gap where it
 * would land. The dragged row keeps its own slot (it is drawn faded out), so
 * each row moves by at most one dragged-row height.
 */
object DragGap {
    /**
     * How many dragged-row heights the row at [index] (counted without the
     * dragged row) moves: +1 down, -1 up, 0 stays. [from] is the dragged row's
     * own index in the list, or -1 when it comes from inside a folder.
     */
    fun slots(index: Int, from: Int, target: Int): Int {
        val real = if (from in 0..index) index + 1 else index
        val wanted = if (index >= target) index + 1 else index
        return wanted - real
    }
}
