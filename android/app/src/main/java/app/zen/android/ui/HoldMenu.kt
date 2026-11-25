package app.zen.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

class MenuAction(val icon: String, val label: String, val destructive: Boolean = false, val run: () -> Unit)

val DestructiveRed = Color(0xFFE5484D)

/**
 * The iOS menu gesture shared by every menu: rows register where they are, a
 * finger that keeps pressing after the menu appears highlights the row under
 * it, and lifting it there runs that row. A plain tap still works as usual.
 */
class SlideSelect {
    private val rows = mutableMapOf<Any, Pair<Rect, () -> Unit>>()
    var hovered by mutableStateOf<Any?>(null)
        private set

    fun register(key: Any, bounds: Rect, action: () -> Unit) {
        rows[key] = bounds to action
    }

    fun reset() {
        rows.clear()
        hovered = null
    }

    /** Returns true when the finger reached a different row, for a haptic tick. */
    fun hover(point: Offset): Boolean {
        val key = rows.entries.firstOrNull { it.value.first.contains(point) }?.key
        val changed = key != null && key != hovered
        hovered = key
        return changed
    }

    fun take(): (() -> Unit)? = hovered?.let { rows[it]?.second }.also { hovered = null }
}

/** Follows the held finger (root coordinates) until it lifts; returns whether it travelled. */
suspend fun AwaitPointerEventScope.followHeldFinger(
    id: PointerId,
    origin: () -> Offset,
    select: SlideSelect,
    haptics: HapticFeedback,
): Boolean {
    var start: Offset? = null
    var moved = false
    val slop = 10.dp.toPx()
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == id } ?: break
        change.consume()
        val point = origin() + change.position
        if (start == null) start = point
        if ((point - start).getDistance() > slop) moved = true
        if (select.hover(point)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (!change.pressed) break
    }
    return moved
}

class HoldMenuState {
    var actions by mutableStateOf<List<MenuAction>>(emptyList())
        private set
    var anchor by mutableStateOf(Rect.Zero)
        private set
    var open by mutableStateOf(false)
        private set
    val select = SlideSelect()

    fun show(at: Rect, menu: List<MenuAction>) {
        select.reset()
        anchor = at
        actions = menu
        open = menu.isNotEmpty()
    }

    fun close() {
        open = false
    }

    /** After the finger lifts: runs the row it rests on, or leaves the menu open for taps if it never moved. */
    fun release(moved: Boolean) {
        val action = select.take()
        if (action != null || moved) close()
        action?.invoke()
    }
}

/** Long-pressing the element opens [state]'s menu next to it; sliding onto an entry and lifting picks it. */
fun Modifier.holdMenu(state: HoldMenuState, actions: () -> List<MenuAction>): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    onGloballyPositioned { bounds = it.boundsInRoot() }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            state.show(bounds, actions())
            val moved = followHeldFinger(down.id, { bounds.topLeft }, state.select, haptics)
            state.release(moved)
        }
    }
}

/** Draws [state]'s menu under its anchor (above when it does not fit), over a dimmed backdrop. */
@Composable
fun BoxScope.HoldMenuHost(state: HoldMenuState) {
    val density = LocalDensity.current
    var host by remember { mutableStateOf(Rect.Zero) }
    var menuHeight by remember { mutableIntStateOf(0) }
    Box(Modifier.matchParentSize().onGloballyPositioned { host = it.boundsInRoot() })
    if (state.open) {
        BackHandler { state.close() }
        Box(
            Modifier
                .matchParentSize()
                .zIndex(4f)
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.close() },
        )
    }
    val gap = with(density) { 6.dp.toPx() }
    val margin = with(density) { 12.dp.toPx() }
    val width = with(density) { 240.dp.toPx() }
    val below = state.anchor.bottom - host.top + gap
    val fitsBelow = below + menuHeight <= host.height - margin
    val y = if (fitsBelow) below else (state.anchor.top - host.top - menuHeight - gap).coerceAtLeast(margin)
    val x = (state.anchor.left - host.left).coerceIn(margin, (host.width - width - margin).coerceAtLeast(margin))
    val pivot = TransformOrigin(0.15f, if (fitsBelow) 0f else 1f)
    AnimatedVisibility(
        state.open,
        modifier = Modifier.zIndex(5f).offset { IntOffset(x.roundToInt(), y.roundToInt()) },
        enter = scaleIn(spring(dampingRatio = 0.72f, stiffness = 520f), initialScale = 0.6f, transformOrigin = pivot) + fadeIn(tween(120)),
        exit = scaleOut(tween(140), targetScale = 0.8f, transformOrigin = pivot) + fadeOut(tween(140)),
    ) {
        MenuCard(state.actions, state.select, Modifier.onGloballyPositioned { menuHeight = it.size.height }) { action ->
            state.close()
            action.run()
        }
    }
}

/** The menu panel itself, shared by the sidebar, the library, the page and the space switcher. */
@Composable
fun MenuCard(actions: List<MenuAction>, select: SlideSelect, modifier: Modifier = Modifier, onPick: (MenuAction) -> Unit) {
    // Sidebars recolour onSurface for their gradient; the card keeps text readable on its own surface.
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val text = if (surface.isLight()) Color(0xFF1A1A1E) else Color.White
    Surface(
        modifier.width(240.dp),
        shape = RoundedCornerShape(14.dp),
        color = surface,
        contentColor = text,
        shadowElevation = 16.dp,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            actions.forEachIndexed { index, action ->
                val tint = if (action.destructive) DestructiveRed else text
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { select.register(index, it.boundsInRoot()) { onPick(action) } }
                        .background(if (select.hovered == index) text.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable { onPick(action) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(action.label, Modifier.weight(1f), color = tint, style = MaterialTheme.typography.bodyLarge)
                    if (action.icon == "edit") {
                        Icon(Icons.Default.Edit, null, Modifier.size(17.dp), tint = tint)
                    } else {
                        Icon(zenIcon(action.icon), null, Modifier.size(17.dp), tint = tint)
                    }
                }
            }
        }
    }
}

/** Registers a row of a custom menu (bar menu, link menu) for the slide-to-pick gesture. */
fun Modifier.slideTarget(select: SlideSelect, key: Any, action: () -> Unit): Modifier =
    onGloballyPositioned { select.register(key, it.boundsInRoot(), action) }

