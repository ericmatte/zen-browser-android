package app.zen.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zen.android.BuildConfig
import app.zen.android.browser.PageRequest
import app.zen.android.browser.SearchEngine
import app.zen.android.browser.UrlInput
import app.zen.android.sync.SyncStatus
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@Composable
fun BoxScope.Overlays(vm: BrowserViewModel) {
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

    vm.contextMenu?.let { ContextMenuSheet(vm, it) }

    AnimatedVisibility(
        vm.showSettings,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        SettingsScreen(vm)
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
fun SessionView(session: GeckoSession, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { GeckoView(it) },
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
                    GlassSurface(shape = RoundedCornerShape(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = vm::closeGlance) { Icon(zenIcon("close"), "Close glance", Modifier.size(16.dp)) }
                            IconButton(onClick = vm::expandGlance) { Icon(zenIcon("arrows-expand"), "Open as tab", Modifier.size(16.dp)) }
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
                    SessionView(session, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun ContextMenuSheet(vm: BrowserViewModel, element: GeckoSession.ContentDelegate.ContextElement) {
    val context = LocalContext.current
    val link = element.linkUri
    val media = element.srcUri
    val target = link ?: media ?: return
    BackHandler { vm.contextMenu = null }
    fun act(block: () -> Unit) {
        vm.contextMenu = null
        block()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.contextMenu = null },
        contentAlignment = Alignment.BottomCenter,
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
                    MenuRow("open", "Glance") { act { vm.openGlance(link) } }
                    MenuRow("plus", "Open in new tab") { act { vm.newTab(link) } }
                    MenuRow("privateBrowsing", "Open in private tab") { act { vm.newPrivateTab(link) } }
                    MenuRow("link", "Copy link") {
                        act {
                            copyToClipboard(context, link)
                            vm.message = "Link copied"
                        }
                    }
                    MenuRow("share", "Share link") { act { share(context, link) } }
                }
                if (media != null && element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE) {
                    MenuRow("open", "Open image") { act { vm.newTab(media) } }
                    MenuRow("edit-copy", "Copy image address") {
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
private fun MenuRow(icon: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
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
        is PageRequest.Permission -> AlertDialog(
            onDismissRequest = { finish(request::cancel) },
            title = { Text(request.title) },
            text = { Text(request.message) },
            confirmButton = { TextButton(onClick = { finish { request.answer(true) } }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { finish { request.answer(false) } }) { Text("Block") } },
        )
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

@Composable
private fun SettingsScreen(vm: BrowserViewModel) {
    BackHandler { vm.showSettings = false }
    val prefs by vm.settings.state.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.showSettings = false }) { Icon(zenIcon("back"), "Back", Modifier.size(18.dp)) }
                Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Section("Sync") {
                    val label = when (val s = status) {
                        SyncStatus.SignedOut -> "Not signed in"
                        SyncStatus.Idle -> "Signed in · spaces are in sync"
                        SyncStatus.Syncing -> "Syncing…"
                        is SyncStatus.Failed -> "Sync error: ${s.message}"
                    }
                    SettingRow("Mozilla account", label)
                    if (status == SyncStatus.SignedOut) {
                        ActionRow("Sign in") {
                            vm.showSettings = false
                            vm.beginLogin()
                        }
                    } else {
                        ActionRow("Sync now") { vm.syncNow() }
                        ActionRow("Sign out", destructive = true) { vm.signOut() }
                    }
                }
                Section("Search") {
                    SearchEngine.entries.forEach { engine ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { vm.settings.update { it.copy(searchEngine = engine) } }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(prefs.searchEngine == engine, null)
                            Text(engine.label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Section("Appearance") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = prefs.themeMode == mode
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        vm.settings.update { it.copy(themeMode = mode) }
                                        vm.applyThemeMode(mode)
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(mode.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SwitchRow("Match status bar to website", "Tints the top of the screen with the page colour", prefs.tintStatusBar) { on ->
                        vm.settings.update { it.copy(tintStatusBar = on) }
                        if (!on) vm.pageTopColor = null else vm.requestCapture()
                    }
                }
                Section("Privacy") {
                    SettingRow("Tracking protection", "Standard · trackers and cross-site cookies blocked")
                    ActionRow("Clear browsing data", destructive = true) { confirmClear = true }
                    ActionRow("Close all private tabs") { vm.closePrivateTabs() }
                }
                Section("About") {
                    SettingRow("Zen for Android", "Version ${BuildConfig.VERSION_NAME} · GeckoView ${org.mozilla.geckoview.BuildConfig.MOZILLA_VERSION}")
                    ActionRow("Source code") {
                        vm.showSettings = false
                        vm.newTab("https://github.com/ericmatte/zen-browser-desktop")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear browsing data?") },
            text = { Text("Cookies, cache and site data are removed. Your synced spaces and tabs stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearBrowsingData()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { content() }
}

@Composable
private fun SettingRow(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionRow(title: String, destructive: Boolean = false, onClick: () -> Unit) {
    Text(
        title,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange)
    }
}
