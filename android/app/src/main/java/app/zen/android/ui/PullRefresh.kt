package app.zen.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.zen.android.browser.PullToRefresh
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

class PullState {
    /** How far the indicator has come down, in pixels. */
    val offset = Animatable(0f)
    var trigger = 1f
    var refreshing by mutableStateOf(false)
}

/** Dragging down on a page already at its top pulls the indicator; letting go past the trigger reloads. */
fun Modifier.pullToRefresh(state: PullState, allowed: () -> Boolean, onRefresh: () -> Unit): Modifier = composed {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val trigger = with(LocalDensity.current) { 150.dp.toPx() }
    state.trigger = trigger
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pulling = false
            var drag = 0f
            var armed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!pulling) {
                    val dy = change.position.y - down.position.y
                    val dx = abs(change.position.x - down.position.x)
                    if (dy > viewConfiguration.touchSlop && dy > dx * 1.5f && event.changes.size == 1 && !state.refreshing && allowed()) {
                        pulling = true
                    } else if (dx > viewConfiguration.touchSlop || dy < -viewConfiguration.touchSlop) {
                        break
                    } else {
                        continue
                    }
                }
                drag = (drag + change.position.y - change.previousPosition.y).coerceAtLeast(0f)
                change.consume()
                if (drag >= trigger != armed) {
                    armed = drag >= trigger
                    if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
                scope.launch { state.offset.snapTo(PullToRefresh.resisted(drag, trigger)) }
            }
            if (!pulling) return@awaitEachGesture
            scope.launch {
                if (armed) {
                    state.refreshing = true
                    onRefresh()
                    state.offset.animateTo(trigger * 0.45f, spring(dampingRatio = 0.8f))
                } else {
                    state.offset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 500f))
                }
            }
        }
    }
}

@Composable
fun PullIndicator(state: PullState, loading: Boolean, modifier: Modifier) {
    val spin = rememberInfiniteTransition(label = "pullSpin")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    // Stays while the reload runs (capped, so a page that never settles cannot keep it on screen).
    LaunchedEffect(state.refreshing) {
        if (!state.refreshing) return@LaunchedEffect
        withTimeoutOrNull(1500) { snapshotFlow { loading }.first { it } }
        withTimeoutOrNull(8000) { snapshotFlow { loading }.first { !it } }
        state.offset.animateTo(0f, tween(220))
        state.refreshing = false
    }
    if (state.offset.value <= 0f && !state.refreshing) return
    val size = with(LocalDensity.current) { 40.dp.toPx() }
    Box(
        modifier.graphicsLayer {
            translationY = state.offset.value - size
            val progress = (state.offset.value / (state.trigger * 0.5f)).coerceIn(0f, 1f)
            alpha = progress
            scaleX = 0.6f + 0.4f * progress
            scaleY = 0.6f + 0.4f * progress
            rotationZ = if (state.refreshing) turn else state.offset.value / state.trigger * 540f
        },
        contentAlignment = Alignment.Center,
    ) {
        Surface(Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
            Box(contentAlignment = Alignment.Center) {
                Icon(zenIcon("reload"), "Reload", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
