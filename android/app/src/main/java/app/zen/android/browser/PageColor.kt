package app.zen.android.browser

/**
 * Picks the colour that covers most of a pixel row taken from the top of the
 * page. A plain average would turn a white header with a coloured logo into
 * a muddy grey; the most frequent colour is what the eye reads as "the bar".
 */
object PageColor {
    fun dominant(pixels: IntArray, minShare: Float = 0.35f): Int? {
        if (pixels.isEmpty()) return null
        val counts = HashMap<Int, Int>()
        for (p in pixels) {
            val opaque = p or 0xFF000000.toInt()
            val bucket = opaque and 0xFFF8F8F8.toInt()
            counts[bucket] = (counts[bucket] ?: 0) + 1
        }
        val (bucket, count) = counts.maxBy { it.value }
        if (count < pixels.size * minShare) return null
        return pixels.first { (it or 0xFF000000.toInt()) and 0xFFF8F8F8.toInt() == bucket } or 0xFF000000.toInt()
    }
}
