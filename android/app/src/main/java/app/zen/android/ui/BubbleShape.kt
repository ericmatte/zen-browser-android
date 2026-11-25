package app.zen.android.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class TailSide { Bottom, Start }

/**
 * A rounded card with a small tail pointing at what opened it, like a chat
 * bubble. The tail sits [tailFromEnd] away from the bottom-right corner
 * (bottom tail) or from the bottom-left corner (start tail), measured to its tip.
 */
class BubbleShape(
    private val side: TailSide,
    private val tailFromEnd: Dp,
    private val corner: Dp = 18.dp,
    val tail: Dp = 7.dp,
) : Shape {
    /** Keeps the content out of the strip the tail lives in. */
    val contentPadding: PaddingValues
        get() = if (side == TailSide.Bottom) PaddingValues(bottom = tail) else PaddingValues(start = tail)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val t = tail.toPx()
        val r = corner.toPx()
        val at = tailFromEnd.toPx()
        val body = Path().apply {
            val left = if (side == TailSide.Start) t else 0f
            val bottom = if (side == TailSide.Bottom) size.height - t else size.height
            addRoundRect(RoundRect(left, 0f, size.width, bottom, CornerRadius(r)))
        }
        val point = Path().apply {
            if (side == TailSide.Bottom) {
                val x = (size.width - at).coerceIn(r + t, size.width - r - t)
                moveTo(x - t * 1.3f, size.height - t - 1f)
                lineTo(x, size.height)
                lineTo(x + t * 1.3f, size.height - t - 1f)
            } else {
                val y = (size.height - at).coerceIn(r + t, size.height - r - t)
                moveTo(t + 1f, y - t * 1.3f)
                lineTo(0f, y)
                lineTo(t + 1f, y + t * 1.3f)
            }
            close()
        }
        Outline.Generic(Path().apply { op(body, point, PathOperation.Union) })
    }
}
