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

    /**
     * The colour a favicon is "about": the most common clearly coloured pixel.
     * Transparent, white-ish and black-ish pixels are skipped, so a logo on a
     * white square still yields its brand colour.
     */
    fun accent(pixels: IntArray): Int? {
        val counts = HashMap<Int, Int>()
        for (p in pixels) {
            if ((p ushr 24) < 160) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            if (max < 40 || min > 225 || max - min < 40) continue
            val bucket = (r and 0xE0 shl 16) or (g and 0xE0 shl 8) or (b and 0xE0)
            counts[bucket] = (counts[bucket] ?: 0) + 1
        }
        val best = counts.maxByOrNull { it.value }?.key ?: return null
        val members = pixels.filter { p ->
            (p ushr 24) >= 160 && ((p shr 16 and 0xE0) shl 16 or ((p shr 8 and 0xE0) shl 8) or (p and 0xE0)) == best
        }
        val r = members.sumOf { it shr 16 and 0xFF } / members.size
        val g = members.sumOf { it shr 8 and 0xFF } / members.size
        val b = members.sumOf { it and 0xFF } / members.size
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
