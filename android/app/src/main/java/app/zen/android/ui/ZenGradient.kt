package app.zen.android.ui

import app.zen.android.sync.SpaceTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

typealias Rgb = DoubleArray

/**
 * Port of the opaque path of src/zen/spaces/ZenGradientGenerator.mjs
 * (getGradient, mixBase, compositeOverUnderlay and the OKLab helpers), so a
 * space looks the same on the phone as in the desktop sidebar.
 */
object ZenGradient {
    private const val MIN_LAYER_ALPHA = 0.2
    private const val MAX_LAYER_ALPHA = 0.88
    private const val GRADIENT_STOPS = 12
    private const val GRADIENT_CHROMA = 0.0
    private const val GRADIENT_HUE_EASE = 0.0
    private const val BASE_GRADIENT_START = 0.0
    private const val BASE_GRADIENT_END = 0.75
    private const val OVERLAY_MAX_ALPHA = 0.9

    /** Range of the desktop opacity slider (theme-picker.inc). */
    private const val MIN_OPACITY = 0.30
    private const val MAX_OPACITY = 0.8

    /** Stops are (position 0..1, ARGB). [overlay] is the third dot, faded in from the top like desktop's overlay strips. */
    data class Spec(val stops: List<Pair<Float, Long>>, val overlay: Long?, val custom: Boolean)

    /** kBlankWindowColors: what desktop paints for a space without a theme. */
    fun blankTheme(dark: Boolean): SpaceTheme = if (dark) {
        SpaceTheme(listOf(0xFF09091DL, 0xFF15091CL), MAX_OPACITY.toFloat())
    } else {
        SpaceTheme(listOf(0xFFE0E0EEL, 0xFFF8E9E4L), MAX_OPACITY.toFloat())
    }

    /** Text and accent colours that sit on a space's gradient. */
    data class Chrome(val lightText: Boolean, val text: Long, val accent: Long)

    /**
     * Port of shouldBeDarkMode, titleColors, toolbarTextStyles and
     * getAccentColorForUI: the text turns light or dark depending on which
     * reads better on the dominant colour, tinted 12% with it, at 90% opacity.
     */
    fun chrome(theme: SpaceTheme?, dark: Boolean): Chrome {
        val t = theme?.takeIf { it.colors.isNotEmpty() } ?: blankTheme(dark)
        val dominant = toRgb(t.dominant)
        val bg = composite(dominant, layerAlpha(t.opacity.toDouble()), underlay(dark))
        val titleLight = blend(dominant, rgb(0.0, 0.0, 0.0), TEXT_TINT)
        val titleDark = blend(dominant, rgb(255.0, 255.0, 255.0), TEXT_TINT)
        val darkText = blend(titleDark, bg, TEXT_OPACITY)
        val lightText = blend(titleLight, bg, TEXT_OPACITY)
        val useLightText = contrastRatio(bg, darkText) > contrastRatio(bg, lightText)
        val title = if (useLightText) titleDark else titleLight
        val textArgb = (argb(title) and 0x00FFFFFFL) or ((TEXT_OPACITY * 255).roundToInt().toLong() shl 24)
        return Chrome(useLightText, textArgb, argb(accentForUi(dominant, useLightText, dark)))
    }

    private const val TEXT_OPACITY = 0.9
    private const val TEXT_TINT = 0.12

    private fun blend(a: Rgb, b: Rgb, p: Double): Rgb = rgb(*DoubleArray(3) { (a[it] * p + b[it] * (1 - p)).roundToInt().toDouble() })

    fun luminance(c: Rgb): Double {
        val v = c.map { x -> (x / 255).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) } }
        return v[0] * 0.2126 + v[1] * 0.7152 + v[2] * 0.0722
    }

    fun contrastRatio(a: Rgb, b: Rgb): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun accentForUi(c: Rgb, useLightText: Boolean, systemDark: Boolean): Rgb {
        if (useLightText || (c[0] == c[1] && c[1] == c[2])) return c
        val (h, s, l) = rgbToHsl(c)
        val target = if (systemDark) 0.62 else 0.42
        return hslToRgb(h, minOf(1.0, s + 0.3), l * 0.4 + target * 0.6)
    }

    private fun rgbToHsl(c: Rgb): Triple<Double, Double, Double> {
        val r = c[0] / 255
        val g = c[1] / 255
        val b = c[2] / 255
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2
        if (max == min) return Triple(0.0, 0.0, l)
        val d = max - min
        val s = if (l > 0.5) d / (2 - max - min) else d / (max + min)
        val h = when (max) {
            r -> (g - b) / d + if (g < b) 6 else 0
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } / 6
        return Triple(h, s, l)
    }

    private fun hslToRgb(h: Double, s: Double, l: Double): Rgb {
        if (s == 0.0) return rgb(l * 255, l * 255, l * 255)
        fun hue(p: Double, q: Double, tIn: Double): Double {
            var t = tIn
            if (t < 0) t += 1
            if (t > 1) t -= 1
            return when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 1.0 / 2 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        return rgb(hue(p, q, h + 1.0 / 3) * 255, hue(p, q, h) * 255, hue(p, q, h - 1.0 / 3) * 255)
    }

    fun spec(theme: SpaceTheme?, dark: Boolean): Spec {
        val underlay = underlay(dark)
        if (theme == null || theme.colors.isEmpty()) return spec(blankTheme(dark), dark)
        val alpha = layerAlpha(theme.opacity.toDouble())
        val rgbs = theme.colors.map(::toRgb)
        if (rgbs.size == 1) {
            val solid = argb(composite(rgbs[0], alpha, underlay))
            return Spec(listOf(0f to solid, 1f to solid), null, false)
        }
        if (theme.custom) {
            val stops = rgbs.mapIndexed { i, c -> i.toFloat() / (rgbs.size - 1) to argb(c) }
            return Spec(stops, null, true)
        }
        val stops = (0 until GRADIENT_STOPS).map { i ->
            val position = i.toDouble() / (GRADIENT_STOPS - 1)
            val t = ((position - BASE_GRADIENT_START) / (BASE_GRADIENT_END - BASE_GRADIENT_START)).coerceIn(0.0, 1.0)
            position.toFloat() to argb(composite(mixBase(rgbs[0], rgbs[1], t, rgbs.getOrNull(2)), alpha, underlay))
        }
        val overlay = rgbs.getOrNull(2)?.let { argb(composite(it, alpha, underlay)) }
        return Spec(stops, overlay, false)
    }

    const val overlayMaxAlpha = OVERLAY_MAX_ALPHA.toFloat()

    fun underlay(dark: Boolean): Rgb = if (dark) rgb(19.0, 19.0, 19.0) else rgb(255.0, 255.0, 255.0)

    fun layerAlpha(opacity: Double): Double {
        val intensity = ((opacity - MIN_OPACITY) / (MAX_OPACITY - MIN_OPACITY)).coerceIn(0.0, 1.0)
        return MIN_LAYER_ALPHA + (MAX_LAYER_ALPHA - MIN_LAYER_ALPHA) * intensity * intensity
    }

    fun composite(c: Rgb, alpha: Double, underlay: Rgb): Rgb =
        rgb(*DoubleArray(3) { i -> (c[i] * alpha + underlay[i] * (1 - alpha)).roundToInt().toDouble() })

    fun mixBase(rgb1: Rgb, rgb2: Rgb, t: Double, via: Rgb? = null): Rgb {
        val lab1 = rgbToOklab(rgb1)
        val lab2 = rgbToOklab(rgb2)
        val plain = DoubleArray(3) { lab1[it] + (lab2[it] - lab1[it]) * t }
        val c1 = hypot(lab1[1], lab1[2])
        val c2 = hypot(lab2[1], lab2[2])
        val h1 = atan2(lab1[2], lab1[1])
        val h2 = atan2(lab2[2], lab2[1])
        fun wrap(angle: Double): Double {
            var a = angle
            while (a > Math.PI) a -= 2 * Math.PI
            while (a < -Math.PI) a += 2 * Math.PI
            return a
        }
        var dh = wrap(h2 - h1)
        val labVia = via?.let(::rgbToOklab)
        if (labVia != null && hypot(labVia[1], labVia[2]) > 1e-4) {
            val toVia = wrap(atan2(labVia[2], labVia[1]) - h1)
            val onShortArc = if (dh >= 0) toVia >= 0 && toVia <= dh else toVia <= 0 && toVia >= dh
            if (!onShortArc) dh -= (if (dh == 0.0) 1.0 else sign(dh)) * 2 * Math.PI
        }
        val smooth = t * t * (3 - 2 * t)
        val eased = t + (smooth - t) * GRADIENT_HUE_EASE
        val hue = if (c1 < 1e-4) h2 else if (c2 < 1e-4) h1 else h1 + dh * eased
        val chroma = c1 + (c2 - c1) * t
        val vivid = doubleArrayOf(plain[0], chroma * cos(hue), chroma * sin(hue))
        return oklabToRgb(DoubleArray(3) { plain[it] + (vivid[it] - plain[it]) * GRADIENT_CHROMA })
    }

    private fun toLinear(c: Double): Double {
        val a = abs(c)
        return sign(c) * if (a <= 0.04045) a / 12.92 else ((a + 0.055) / 1.055).pow(2.4)
    }

    private fun toGamma(c: Double): Double {
        val a = abs(c)
        return sign(c) * if (a <= 0.0031308) a * 12.92 else 1.055 * a.pow(1 / 2.4) - 0.055
    }

    private fun srgbToLinear(channel: Double) = toLinear(channel / 255)
    private fun linearToSrgb(c: Double) = (toGamma(c) * 255).coerceIn(0.0, 255.0)

    fun rgbToOklab(color: Rgb): DoubleArray {
        val lr = srgbToLinear(color[0])
        val lg = srgbToLinear(color[1])
        val lb = srgbToLinear(color[2])
        val l = cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
        val m = cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
        val s = cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
        return doubleArrayOf(
            0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
        )
    }

    fun oklabToRgb(color: DoubleArray): Rgb {
        val l = (color[0] + 0.3963377774 * color[1] + 0.2158037573 * color[2]).pow(3)
        val m = (color[0] - 0.1055613458 * color[1] - 0.0638541728 * color[2]).pow(3)
        val s = (color[0] - 0.0894841775 * color[1] - 1.291485548 * color[2]).pow(3)
        return rgb(
            linearToSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            linearToSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            linearToSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s),
        )
    }

    private fun rgb(vararg c: Double): Rgb = doubleArrayOf(c[0], c[1], c[2])
    private fun toRgb(argb: Long): Rgb = rgb(((argb shr 16) and 0xFF).toDouble(), ((argb shr 8) and 0xFF).toDouble(), (argb and 0xFF).toDouble())
    fun argb(c: Rgb): Long = 0xFF000000L or (c[0].roundToInt().toLong() shl 16) or (c[1].roundToInt().toLong() shl 8) or c[2].roundToInt().toLong()
}
