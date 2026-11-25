package app.zen.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.zen.android.browser.PageRequest
import app.zen.android.browser.UrlInput
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@Composable
fun BoxScope.Overlays(vm: BrowserViewModel, dark: Boolean, linkMenu: SlideSelect) {
    vm.glance?.let { GlanceOverlay(vm, it) }

    vm.loginSession?.let { login ->
        BackHandler { vm.cancelLogin() }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::cancelLogin) { Icon(zenIcon("close"), "Cancel sign-in", Modifier.size(18.dp)) }
                    Text("Sign in to sync with Zen", style = MaterialTheme.typography.titleMedium)
                }
                SessionView(login, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    ContextMenuSheet(vm, linkMenu)

    AnimatedVisibility(
        vm.showSettings,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        SettingsScreen(vm, dark)
    }

    vm.requests.current?.let { PageDialog(it, onDone = vm.requests::resolved) }

    val message = vm.message
    AnimatedVisibility(
        message != null,
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 8.dp) {
            Text(message.orEmpty(), Modifier.padding(horizontal = 18.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2200)
            vm.message = null
        }
    }
}

@Composable
fun SessionView(session: GeckoSession, modifier: Modifier, texture: Boolean = false) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // A TextureView honours Compose clipping and scaling (rounded, animated cards); a SurfaceView does not.
            GeckoView(context).apply {
                if (texture) setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                setAutofillEnabled(true)
                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_YES
            }
        },
        update = { view ->
            if (view.session !== session) {
                view.releaseSession()
                view.setSession(session)
            }
        },
        onRelease = { it.releaseSession() },
    )
}

/** Desktop's Glance: a link previewed in a floating card over the current page. */
@Composable
private fun GlanceOverlay(vm: BrowserViewModel, session: GeckoSession) {
    BackHandler { vm.closeGlance() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.closeGlance() },
    ) {
        AnimatedVisibility(shown, enter = scaleIn(initialScale = 0.9f) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 6.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val tint = MaterialTheme.colorScheme.onSurface
                            IconButton(onClick = vm::closeGlance) { Icon(zenIcon("close"), "Close glance", Modifier.size(18.dp), tint = tint) }
                            IconButton(onClick = vm::expandGlance) { Icon(zenIcon("arrows-expand"), "Open as tab", Modifier.size(18.dp), tint = tint) }
                        }
                    }
                    Text(
                        UrlInput.display(vm.glanceUrl),
                        Modifier.weight(1f).padding(start = 12.dp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    SessionView(session, Modifier.fillMaxSize(), texture = true)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ContextMenuSheet(vm: BrowserViewModel, select: SlideSelect) {
    val context = LocalContext.current
    val current = vm.contextMenu
    var shown by remember { mutableStateOf<GeckoSession.ContentDelegate.ContextElement?>(null) }
    if (current != null) shown = current
    val element = shown ?: return
    val link = element.linkUri
    val media = element.srcUri
    val target = link ?: media ?: return
    if (current != null) BackHandler { vm.contextMenu = null }
    fun act(block: () -> Unit) {
        vm.contextMenu = null
        block()
    }
    AnimatedVisibility(current != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.contextMenu = null },
        )
    }
    AnimatedVisibility(
        current != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { it } + fadeIn(tween(120)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
    ) {
        Surface(
            Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    element.title ?: element.altText ?: target,
                    Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                if (link != null) {
                    if (vm.settings.state.value.glance) MenuRow(select, "open", "Glance") { act { vm.openGlance(link) } }
                    if (!vm.privateMode) MenuRow(select, "plus", "Open in new tab") { act { vm.newTab(link) } }
                    MenuRow(select, "privateBrowsing", "Open in private tab") { act { vm.newPrivateTab(link) } }
                    MenuRow(select, "link", "Copy link") {
                        act {
                            copyToClipboard(context, link)
                            vm.message = "Link copied"
                        }
                    }
                    MenuRow(select, "share", "Share link") { act { share(context, link) } }
                }
                if (media != null && element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE) {
                    MenuRow(select, "open", "Open image") { act { vm.newTab(media) } }
                    MenuRow(select, "edit-copy", "Copy image address") {
                        act {
                            copyToClipboard(context, media)
                            vm.message = "Image address copied"
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(select: SlideSelect, icon: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .slideTarget(select, label, onClick)
            .background(if (select.hovered == label) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(zenIcon(icon), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PageDialog(request: PageRequest, onDone: () -> Unit) {
    fun finish(block: () -> Unit) {
        block()
        onDone()
    }
    val title = request.title.takeIf { it.isNotBlank() }
    when (request) {
        is PageRequest.Alert -> AlertDialog(
            onDismissRequest = { finish(request::cancel) },
            title = title?.let { { Text(it) } },
            text = { Text(request.message) },
            confirmButton = { TextButton(onClick = { finish(request.done) }) { Text("OK") } },
        )
        is PageRequest.Confirm -> AlertDialog(
            onDismissRequest = { finish(request::cancel) },
            title = title?.let { { Text(it) } },
            text = { Text(request.message) },
            confirmButton = { TextButton(onClick = { finish { request.answer(true) } }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { finish { request.answer(false) } }) { Text("Cancel") } },
        )
        is PageRequest.Permission -> {
            var remember by remember(request) { mutableStateOf(true) }
            AlertDialog(
                onDismissRequest = { finish(request::cancel) },
                title = { Text(request.title) },
                text = {
                    Column {
                        Text(request.message)
                        if (request.canRemember) {
                            Row(
                                Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).clickable { remember = !remember },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(remember, { remember = it })
                                Text("Remember this decision")
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { finish { request.answer(true, remember) } }) { Text("Allow") } },
                dismissButton = { TextButton(onClick = { finish { request.answer(false, remember) } }) { Text("Block") } },
            )
        }
        is PageRequest.TextInput -> {
            var text by remember(request) { mutableStateOf(request.initial) }
            AlertDialog(
                onDismissRequest = { finish(request::cancel) },
                title = title?.let { { Text(it) } },
                text = {
                    Column {
                        if (request.message.isNotBlank()) Text(request.message, Modifier.padding(bottom = 8.dp))
                        OutlinedTextField(text, { text = it }, singleLine = true)
                    }
                },
                confirmButton = { TextButton(onClick = { finish { request.answer(text) } }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { finish(request::cancel) }) { Text("Cancel") } },
            )
        }
        is PageRequest.Choice -> {
            val picked = remember(request) { mutableStateListOf(*request.options.filter { it.selected }.map { it.id }.toTypedArray()) }
            AlertDialog(
                onDismissRequest = { finish(request::cancel) },
                title = title?.let { { Text(it) } },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        request.options.forEach { option ->
                            if (option.header) {
                                Text(option.label, Modifier.padding(top = 10.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                return@forEach
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = option.enabled) {
                                        if (request.multiple) {
                                            if (option.id in picked) picked.remove(option.id) else picked.add(option.id)
                                        } else {
                                            finish { request.answer(listOf(option.id)) }
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (request.multiple) {
                                    Checkbox(option.id in picked, null, enabled = option.enabled)
                                } else {
                                    RadioButton(option.selected, null, enabled = option.enabled)
                                }
                                Text(option.label, Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    if (request.multiple) TextButton(onClick = { finish { request.answer(picked.toList()) } }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { finish(request::cancel) }) { Text("Cancel") } },
            )
        }
    }
}
