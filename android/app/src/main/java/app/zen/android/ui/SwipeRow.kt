package app.zen.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.zen.android.browser.SwipeOutcome
import app.zen.android.browser.SwipeSettle
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class SwipeAction(val icon: String, val label: String, val color: Color, val run: () -> Unit)

/** At most one row of a list is open at a time; a touch anywhere else in the list closes it. */
class RevealGroup {
    var open by mutableStateOf<Any?>(null)
    internal var openRow = Rect.Zero
    internal var openButton = Rect.Zero
    internal var containerOrigin = Offset.Zero
}

/** Put on the list container: a touch outside the open row closes it and does nothing else. */
fun Modifier.closesReveal(group: RevealGroup): Modifier = pointerInput(group) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (group.open == null) return@awaitEachGesture
        val root = group.containerOrigin + down.position
        if (!group.openRow.contains(root) && !group.openButton.contains(root)) {
            group.open = null
            down.consume()
        }
    }
}.onGloballyPositioned { group.containerOrigin = it.boundsInRoot().topLeft }

val RevealButtonWidth: Dp = 72.dp

/**
 * A list row that follows the finger sideways. Right-to-left uncovers [reveal]
 * (a button that stays until tapped, or until a touch lands elsewhere) or, for
 * rows without one, commits [commitEnd] on a long swipe. Left-to-right commits
 * [commitStart]. Only the uncovered strip is coloured; the row itself stays on
 * the list's real background.
 */
@Composable
fun SwipeRow(
    key: Any,
    group: RevealGroup,
    reveal: SwipeAction?,
    commitStart: SwipeAction?,
    commitEnd: SwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val revealPx = with(LocalDensity.current) { RevealButtonWidth.toPx() }
    val offset = remember(key) { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    var wasRevealed by remember { mutableStateOf(false) }
    val settle = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)
    val isOpen = group.open == key

    LaunchedEffect(isOpen) {
        if (!isOpen && offset.targetValue != 0f) scope.launch { offset.animateTo(0f, settle) }
    }

    Box(
        modifier
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .onGloballyPositioned { if (isOpen) group.openRow = it.boundsInRoot() }
            .draggable(
                orientation = Orientation.Horizontal,
                onDragStarted = { wasRevealed = isOpen },
                state = rememberDraggableState { dx ->
                    scope.launch { offset.snapTo((offset.value + dx).coerceIn(-width.toFloat(), width.toFloat())) }
                },
                // Settling runs in the row's own scope: the drag's coroutine is cancelled when the row recomposes
                // (it does, as it opens), which used to leave the row stuck between open and closed.
                onDragStopped = { velocity ->
                    val outcome = SwipeSettle.decide(
                        offset.value, velocity, width.toFloat(), revealPx, wasRevealed,
                        canReveal = reveal != null, canCommitStart = commitStart != null, canCommitEnd = commitEnd != null,
                    )
                    val target = if (outcome == SwipeOutcome.REVEAL) -revealPx else 0f
                    scope.launch { offset.animateTo(target, settle) }
                    when (outcome) {
                        SwipeOutcome.REVEAL -> group.open = key
                        SwipeOutcome.COMMIT_START -> {
                            if (group.open == key) group.open = null
                            commitStart?.run?.invoke()
                        }
                        SwipeOutcome.COMMIT_END -> commitEnd?.run?.invoke()
                        SwipeOutcome.CLOSE -> if (group.open == key) group.open = null
                    }
                },
            ),
    ) {
        val value = offset.value
        if (value != 0f) {
            val toEnd = value < 0f
            val action = if (toEnd) reveal ?: commitEnd else commitStart
            if (action != null) {
                val strip = with(LocalDensity.current) { abs(value).toDp() }
                val progress = (abs(value) / (if (toEnd && reveal != null) revealPx else width * 0.35f)).coerceIn(0f, 1f)
                // Sized by the row itself, so the strip never grows past the row's height.
                Box(Modifier.matchParentSize(), contentAlignment = if (toEnd) Alignment.CenterEnd else Alignment.CenterStart) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(strip)
                        .clip(RoundedCornerShape(10.dp))
                        .background(action.color.copy(alpha = 0.45f + 0.5f * progress)),
                    contentAlignment = if (toEnd) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    val button = toEnd && reveal != null
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(RevealButtonWidth)
                            .then(
                                if (button) {
                                    Modifier
                                        .onGloballyPositioned { group.openButton = it.boundsInRoot() }
                                        .clickable {
                                            group.open = null
                                            action.run()
                                        }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(zenIcon(action.icon), if (button) action.label else null, Modifier.size(18.dp), tint = Color.White)
                    }
                }
                }
            }
        }
        Box(Modifier.offset { IntOffset(value.roundToInt(), 0) }) {
            content()
            // An open row only closes on tap; it does not also open what it shows.
            if (isOpen) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { group.open = null },
                )
            }
        }
    }
}
