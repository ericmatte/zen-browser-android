package app.zen.android.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import app.zen.android.sync.SpaceTheme

/** Values from src/zen/tokens (branding) and ZenGradientGenerator.getToolbarModifiedBaseRaw. */
object ZenPalette {
    val coral = Color(0xFFF76F53)
    val brandingDark = Color(0xFF101010)
    val brandingPaper = Color(0xFFE2E2E2)
    val toolbarDark = Color(23, 23, 26)
    val toolbarLight = Color(240, 240, 244)
    val iconBackground = Color(0xFF1C1C1C)
    val iconRings = Color(0xFFF2F0E3)
}

fun toolbarBase(dark: Boolean) = if (dark) ZenPalette.toolbarDark else ZenPalette.toolbarLight

/**
 * Paints a space's desktop gradient with the geometry of zen-browser-ui.css
 * (--zen-gradient-dx/dy/x0/y0): a line through the box's centre whose slope
 * follows its aspect ratio, plus the third colour fading in from the top.
 */
fun Modifier.zenSpaceBackground(spec: ZenGradient.Spec): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    if (spec.custom) {
        val rad = Math.toRadians(-30.0 - 90.0)
        val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
        val half = (abs(w * dir.x) + abs(h * dir.y)) / 2
        drawRect(Brush.linearGradient(*spec.stops.map { it.first to Color(it.second) }.toTypedArray(), start = center - dir * half, end = center + dir * half))
        return@drawBehind
    }
    val tan = h / w
    val dx = min(w, w / tan)
    val dy = min(h, h * tan)
    val start = Offset((w - dx) / 2, (h - dy) / 2)
    drawRect(Brush.linearGradient(*spec.stops.map { it.first to Color(it.second) }.toTypedArray(), start = start, end = start + Offset(dx, dy)))
    spec.overlay?.let { overlay ->
        val c = Color(overlay)
        drawRect(Brush.verticalGradient(listOf(c.copy(alpha = 0f), c.copy(alpha = ZenGradient.overlayMaxAlpha))))
    }
}

fun SpaceTheme?.accent(): Color? = this?.colors?.firstOrNull()?.let { Color(it) }

fun Color.isLight() = luminance() > 0.5f

private fun neutralScheme(dark: Boolean, accent: Color): ColorScheme {
    val onAccent = if (accent.isLight()) Color(0xFF111111) else Color.White
    return if (dark) {
        val base = ZenPalette.toolbarDark
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(base, accent, 0.3f),
            onPrimaryContainer = Color.White,
            secondary = lerp(Color(0xFFB8B8BE), accent, 0.2f),
            onSecondary = Color(0xFF111111),
            secondaryContainer = lerp(base, accent, 0.2f),
            onSecondaryContainer = Color(0xFFEDEDF0),
            tertiary = accent,
            background = base,
            onBackground = Color(0xFFEDEDF0),
            surface = base,
            onSurface = Color(0xFFEDEDF0),
            surfaceVariant = Color(0xFF2A2A2F),
            onSurfaceVariant = Color(0xFFA9A9B0),
            surfaceContainerLowest = ZenPalette.brandingDark,
            surfaceContainerLow = Color(0xFF1C1C20),
            surfaceContainer = Color(0xFF202025),
            surfaceContainerHigh = Color(0xFF26262B),
            surfaceContainerHighest = Color(0xFF2E2E34),
            surfaceTint = Color.Transparent,
            outline = Color(0xFF55555C),
            outlineVariant = Color(0xFF34343A),
            error = Color(0xFFFF6B6B),
            scrim = Color.Black,
        )
    } else {
        val base = ZenPalette.toolbarLight
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(base, accent, 0.25f),
            onPrimaryContainer = Color(0xFF111111),
            secondary = lerp(Color(0xFF55555C), accent, 0.2f),
            onSecondary = Color.White,
            secondaryContainer = lerp(base, accent, 0.18f),
            onSecondaryContainer = Color(0xFF1A1A1E),
            tertiary = accent,
            background = base,
            onBackground = Color(0xFF1A1A1E),
            surface = base,
            onSurface = Color(0xFF1A1A1E),
            surfaceVariant = Color(0xFFE3E3E8),
            onSurfaceVariant = Color(0xFF5E5E66),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF7F7FA),
            surfaceContainer = Color(0xFFEDEDF1),
            surfaceContainerHigh = Color(0xFFE7E7EC),
            surfaceContainerHighest = Color(0xFFE0E0E6),
            surfaceTint = Color.Transparent,
            outline = Color(0xFFA0A0A8),
            outlineVariant = Color(0xFFD4D4DA),
            error = Color(0xFFD63B3B),
            scrim = Color.Black,
        )
    }
}

/** Desktop's default accent is the OS accent color (zen.theme.accent-color = AccentColor). */
@Composable
fun systemAccent(): Color {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(context.getColor(android.R.color.system_accent1_400))
    } else {
        ZenPalette.coral
    }
}

@Composable
fun ZenTheme(dark: Boolean, accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = neutralScheme(dark, accent), content = content)
}
