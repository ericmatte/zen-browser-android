package app.zen.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * Shrinks the element a little while it is pressed and springs it back on
 * release. It only watches the touch, so the element's own tap, hold and
 * slide handling keep working. Even a quick tap plays the full dip.
 */
fun Modifier.pressScale(pressed: Float = 0.82f): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val dip = scope.launch { scale.animateTo(pressed, spring(stiffness = 1400f)) }
            waitForUpOrCancellation(PointerEventPass.Initial)
            scope.launch {
                dip.join()
                scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f))
            }
        }
    }
}
