package app.zen.android.browser

object FaviconTone {
    /** Average brightness (0 black, 1 white) of the visible pixels of an ARGB icon, or null when it is fully transparent. */
    fun luminance(pixels: IntArray): Float? {
        var weight = 0f
        var sum = 0f
        for (p in pixels) {
            val a = (p ushr 24 and 0xFF) / 255f
            if (a < 0.1f) continue
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            sum += a * (0.2126f * r + 0.7152f * g + 0.0722f * b)
            weight += a
        }
        return if (weight == 0f) null else sum / weight
    }

    /** A near-black icon on a dark surface, or a near-white one on a light surface, would melt into it. */
    fun blendsIn(luminance: Float, darkSurface: Boolean): Boolean = if (darkSurface) luminance < 0.18f else luminance > 0.88f
}
