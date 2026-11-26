package app.zen.android.ui

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

val PrivateBackdrop = Color(0xFF09090B)

/**
 * The new-tab search, modelled on desktop's urlbar spotlight. The search
 * field itself is the bottom bar morphing in place; this is the backdrop that
 * hides the page behind it. In private mode it never shows history.
 */
@Composable
fun BoxScope.LauncherBackdrop(vm: BrowserViewModel) {
    AnimatedVisibility(vm.launcherOpen, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
        val backdrop = if (vm.privateMode) {
            Brush.verticalGradient(listOf(PrivateTint, PrivateBackdrop))
        } else {
            SolidColor(MaterialTheme.colorScheme.background)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(backdrop)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.closeLauncher() },
        ) {
            // Sits just above the docked search field, which rides on the keyboard; suggestions take its place.
            if (vm.privateMode && vm.suggestions.isEmpty()) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(bottom = EditBarHeight + 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(zenIcon("privateBrowsing"), null, Modifier.size(22.dp), tint = Color.White.copy(alpha = 0.85f))
                    Text("Private", Modifier.padding(start = 10.dp), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
