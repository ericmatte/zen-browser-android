package app.zen.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The new-tab search, modelled on desktop's urlbar spotlight. It opens at
 * launch and for every new tab, keyboard up, and stays empty until the user
 * types. In private mode it never shows history.
 */
@Composable
fun BoxScope.Launcher(vm: BrowserViewModel) {
    AnimatedVisibility(
        vm.launcherOpen,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 12 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 12 },
    ) {
        BackHandler { vm.closeLauncher() }
        var text by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            focus.requestFocus()
            keyboard?.show()
        }
        val onSurface = MaterialTheme.colorScheme.onSurface
        val backdrop = if (vm.privateMode) {
            Brush.verticalGradient(listOf(PrivateTint, Color(0xFF15101F)))
        } else {
            SolidColor(MaterialTheme.colorScheme.background)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(backdrop)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.closeLauncher() },
        ) {
            Column(Modifier.statusBarsPadding().imePadding().padding(12.dp)) {
                if (vm.privateMode) {
                    Row(Modifier.padding(start = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(zenIcon("privateBrowsing"), null, Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.8f))
                        Text("Private", Modifier.padding(start = 8.dp), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Surface(
                    Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    shape = RoundedCornerShape(22.dp),
                    color = if (vm.privateMode) Color(0xFF241A38) else MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 12.dp,
                ) {
                    Column {
                        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(zenIcon("search-glass"), null, Modifier.size(20.dp), tint = onSurface.copy(alpha = 0.6f))
                            BasicTextField(
                                value = text,
                                onValueChange = {
                                    text = it
                                    vm.suggest(it)
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge.copy(color = onSurface, fontSize = 20.sp),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                                keyboardActions = KeyboardActions(onGo = { vm.launch(text) }),
                                modifier = Modifier.weight(1f).padding(start = 16.dp).focusRequester(focus),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (text.isEmpty()) {
                                            Text("Search...", color = onSurface.copy(alpha = 0.45f), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                                        }
                                        inner()
                                    }
                                },
                            )
                            if (text.isNotEmpty()) {
                                IconButton(onClick = {
                                    text = ""
                                    vm.suggest("")
                                }) { Icon(zenIcon("close"), "Clear", Modifier.size(16.dp), tint = onSurface) }
                            }
                        }
                        if (text.isNotBlank() && vm.suggestions.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = onSurface.copy(alpha = 0.1f))
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                                vm.suggestions.forEach { suggestion ->
                                    SuggestionRow(suggestion) { vm.pick(suggestion, inNewTab = true) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
