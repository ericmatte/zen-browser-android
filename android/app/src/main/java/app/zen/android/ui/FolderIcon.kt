package app.zen.android.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.zen.android.sync.ZenIcon
import kotlin.math.tan

/** The folder SVG from src/zen/folders/ZenFolder.mjs (rawIcon), drawn in its 27×27 viewBox. */
private const val BACK_PATH =
    "M8 5.625H11.9473C12.4866 5.625 13.0105 5.80861 13.4316 6.14551L14.2881 6.83105C14.9308 7.34508 15.7298 7.625 16.5527 7.625H20C21.3117 7.625 22.375 8.68832 22.375 10V20C22.375 21.3117 21.3117 22.375 20 22.375H8C6.68832 22.375 5.625 21.3117 5.625 20V8C5.625 6.68832 6.68832 5.625 8 5.625Z"
private const val VIEWBOX = 27f

/** zen-folder-icon.inc.css: `cubic-bezier(0.42, 0, 0, 1)` over 0.3s. */
private val FolderEasing = CubicBezierEasing(0.42f, 0f, 0f, 1f)

/**
 * Port of ZenFolder's animated icon. [open] applies the `[state="open"]`
 * skew/translate/scale; [active] (collapsed with the selected tab inside)
 * swaps the user icon for the three dots, like `[active="true"]`.
 */
@Composable
fun FolderIcon(icon: ZenIcon?, open: Boolean, active: Boolean, size: Dp) {
    val t by animateFloatAsState(if (open) 1f else 0f, tween(300, easing = FolderEasing), label = "folderOpen")
    val dotsAlpha by animateFloatAsState(if (active) 1f else 0f, tween(300, easing = FolderEasing), label = "folderDots")
    val colors = folderColors()
    val back = remember { PathParser().parsePathString(BACK_PATH).toPath() }
    val context = LocalContext.current
    val vector = (icon as? ZenIcon.Bundled)?.let { remember(it.name) { ZenIconSet.get(context, it.name) } }
    val painter = vector?.let { rememberVectorPainter(it) }
    val measurer = rememberTextMeasurer()
    val iconTint = lerp(colors.stroke, MaterialTheme.colorScheme.onSurface, 0.5f).copy(alpha = 0.9f)

    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / VIEWBOX
        withTransform({ scale(unit, unit, Offset.Zero) }) {
            val backPath = transformed(back, skewDeg = 16f * t, tx = -4f * t, ty = 2f * t, scale = 1f - 0.15f * t)
            drawPath(backPath, colors.behind)
            drawPath(backPath, gloss(5.625f, 22.375f), alpha = 0.1f)
            drawPath(backPath, colors.stroke, style = Stroke(1.5f))

            val front = Path().apply { addRoundRect(RoundRect(5.625f, 9.625f, 22.375f, 22.375f, CornerRadius(2.375f))) }
            val frontPath = transformed(front, skewDeg = -16f * t, tx = 8f * t, ty = 2f * t, scale = 1f - 0.15f * t)
            drawPath(frontPath, colors.front)
            drawPath(frontPath, gloss(9.625f, 22.375f), alpha = 0.1f)
            drawPath(frontPath, colors.stroke, style = Stroke(1.5f))

            frontTransform(t) {
                if (dotsAlpha > 0f) {
                    listOf(10f, 14f, 18f).forEach { cx -> drawCircle(colors.stroke, 1.25f, Offset(cx, 16f), alpha = dotsAlpha) }
                }
                val iconAlpha = 1f - dotsAlpha
                if (iconAlpha > 0f) {
                    withTransform({ translate(8.5f, 10.5f) }) {
                        when {
                            painter != null -> with(painter) {
                                draw(androidx.compose.ui.geometry.Size(11f, 11f), alpha = iconAlpha, colorFilter = ColorFilter.tint(iconTint))
                            }
                            icon is ZenIcon.Emoji -> drawText(
                                measurer.measure(icon.text, TextStyle(fontSize = (9f / density / fontScale).sp)),
                                topLeft = Offset(0.5f, -0.5f),
                                alpha = iconAlpha,
                            )
                        }
                    }
                }
            }
        }
    }
}

private class FolderColors(val behind: Color, val front: Color, val stroke: Color)

/** --zen-folder-behind-bgcolor / -front-bgcolor / -stroke from zen-folders.css. */
@Composable
private fun folderColors(): FolderColors {
    val primary = MaterialTheme.colorScheme.primary
    val dark = !MaterialTheme.colorScheme.surface.isLight()
    return remember(primary, dark) {
        if (dark) {
            val zenColorsPrimary = lerp(ZenPalette.brandingDark, primary, 0.2f)
            FolderColors(
                behind = lerp(Color(0xFFC1C1C1), primary, 0.6f),
                front = lerp(Color.Black, primary, 0.6f),
                stroke = lerp(Color(0xFFEBEBEB), zenColorsPrimary, 0.15f),
            )
        } else {
            FolderColors(
                behind = lerp(Color.Gray, primary, 0.6f),
                front = lerp(Color.White, primary, 0.3f),
                stroke = lerp(Color.Black, primary, 0.5f),
            )
        }
    }
}

private fun gloss(top: Float, bottom: Float) = Brush.verticalGradient(listOf(Color.White, Color.Black), startY = top, endY = bottom)

/** CSS `skewX(a) translate(tx, ty) scale(s)` around the SVG origin. */
private fun transformed(source: Path, skewDeg: Float, tx: Float, ty: Float, scale: Float): Path {
    val matrix = android.graphics.Matrix().apply {
        setScale(scale, scale)
        postTranslate(tx, ty)
        postSkew(tan(Math.toRadians(skewDeg.toDouble())).toFloat(), 0f)
    }
    val out = android.graphics.Path(source.asAndroidPath())
    out.transform(matrix)
    return Path().apply { asAndroidPath().set(out) }
}

private inline fun DrawScope.frontTransform(t: Float, block: DrawScope.() -> Unit) {
    val skew = tan(Math.toRadians(-16.0 * t)).toFloat()
    val s = 1f - 0.15f * t
    withTransform({
        transform(
            androidx.compose.ui.graphics.Matrix(
                floatArrayOf(
                    s, 0f, 0f, 0f,
                    skew * s, s, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    8f * t + skew * 2f * t, 2f * t, 0f, 1f,
                ),
            ),
        )
    }, block)
}
