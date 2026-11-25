package app.zen.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.zen.android.browser.UrlInput
import app.zen.android.sync.TabItem
import kotlin.math.abs

@Composable
fun BottomChrome(
    vm: BrowserViewModel,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSidebar: () -> Unit,
    recents: RecentsMenu,
    swipe: PageSwipe,
    modifier: Modifier,
) {
    val dock by animateFloatAsState(if (editing) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 420f), label = "dock")
    Column(modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        AnimatedVisibility(editing && vm.suggestions.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            SuggestionsPanel(vm) { onEditingChange(false) }
        }
        AnimatedVisibility(
            visible = vm.chromeVisible || editing,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            AddressBar(vm, editing, dock, onEditingChange, onSidebar, recents, swipe)
        }
    }
}

/**
 * At rest the bar floats and shows the page title. Tapping it reveals the
 * URL and the bar docks full width on top of the keyboard.
 */
@Composable
private fun AddressBar(
    vm: BrowserViewModel,
    editing: Boolean,
    dock: Float,
    onEditingChange: (Boolean) -> Unit,
    onSidebar: () -> Unit,
    recents: RecentsMenu,
    swipe: PageSwipe,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var menu by remember { mutableStateOf(false) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    fun stopEditing() {
        onEditingChange(false)
        vm.clearSuggestions()
        focusManager.clearFocus()
    }

    val side = lerp(12.dp, 0.dp, dock)
    val vertical = lerp(BarMargin, 0.dp, dock)
    val topCorner = lerp(26.dp, 18.dp, dock)
    val bottomCorner = lerp(26.dp, 0.dp, dock)

    GlassSurface(
        modifier = Modifier.padding(horizontal = side, vertical = vertical).fillMaxWidth().height(BarHeight),
        shape = RoundedCornerShape(topStart = topCorner, topEnd = topCorner, bottomStart = bottomCorner, bottomEnd = bottomCorner),
        tint = if (vm.privateMode) PrivateTint else null,
        solidity = dock,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = lerp(0.dp, 6.dp, dock)), verticalAlignment = Alignment.CenterVertically) {
            if (editing) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(zenIcon("search-glass"), null, Modifier.size(18.dp), tint = onSurface.copy(alpha = 0.7f))
                }
                LaunchedEffect(Unit) {
                    field = TextFieldValue(vm.url, TextRange(0, vm.url.length))
                    focus.requestFocus()
                }
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        vm.suggest(it.text)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = onSurface),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = {
                        vm.navigate(field.text)
                        stopEditing()
                    }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (field.text.isEmpty()) Text("Search or enter address", color = onSurface.copy(alpha = 0.5f))
                            inner()
                        }
                    },
                )
                IconButton(onClick = {
                    copyToClipboard(context, vm.url)
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    vm.message = "Link copied"
                    stopEditing()
                }) {
                    Icon(zenIcon("link"), "Copy link", Modifier.size(18.dp), tint = onSurface)
                }
                IconButton(onClick = { if (field.text.isEmpty()) stopEditing() else field = TextFieldValue("") }) {
                    Icon(zenIcon("close"), "Clear", Modifier.size(16.dp), tint = onSurface)
                }
            } else {
                SidebarButton(vm, onSidebar, recents)
                TitleLabel(
                    vm = vm,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pageSwipe(vm, swipe)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onEditingChange(true) },
                )
                IconButton(onClick = vm::openLauncher) {
                    Icon(zenIcon("plus"), "New tab", Modifier.size(18.dp), tint = onSurface)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(zenIcon("menu"), "More", Modifier.size(18.dp), tint = onSurface) }
                    BarMenu(vm, menu, onDismiss = { menu = false })
                }
            }
        }
        if (vm.progress in 1..99) {
            val progress by animateFloatAsState(vm.progress / 100f, label = "progress")
            Box(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp).fillMaxWidth(progress).height(2.dp)
                    .clip(RoundedCornerShape(1.dp)).background(accent),
            )
        }
    }
}

/** Horizontal drags walk the tabs with a full-page slide; an upward flick opens the new-tab search. */
private fun Modifier.pageSwipe(vm: BrowserViewModel, swipe: PageSwipe): Modifier = pointerInput(vm.activeTab) {
    val tracker = VelocityTracker()
    var total = Offset.Zero
    var horizontal: Boolean? = null
    detectDragGestures(
        onDragStart = {
            tracker.resetTracking()
            total = Offset.Zero
            horizontal = null
        },
        onDrag = { change, amount ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            total += amount
            if (horizontal == null) horizontal = abs(total.x) >= abs(total.y)
            if (horizontal == true) swipe.drag(amount.x)
        },
        onDragEnd = {
            when (horizontal) {
                true -> swipe.release(tracker.calculateVelocity().x)
                false -> if (total.y < -48.dp.toPx()) vm.openLauncher()
                null -> Unit
            }
        },
        onDragCancel = { if (horizontal == true) swipe.release(0f) },
    )
}

@Composable
private fun TitleLabel(vm: BrowserViewModel, modifier: Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (vm.url.startsWith("http") && !vm.secure) {
            Icon(zenIcon("security-broken"), "Not secure", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = vm.title.ifBlank { UrlInput.display(vm.url) }.ifEmpty { "Search or enter address" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** Tap opens the sidebar; holding it shows recent tabs, and sliding onto one then lifting the finger opens it (like iOS). */
@Composable
private fun SidebarButton(vm: BrowserViewModel, onSidebar: () -> Unit, recents: RecentsMenu) {
    val haptics = LocalHapticFeedback.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val tint = if (vm.privateMode) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    var lifted = false
                    val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        val up = waitForUpOrCancellation()
                        lifted = true
                        up != null
                    }
                    if (lifted) {
                        if (tapped == true) onSidebar()
                        return@awaitEachGesture
                    }
                    val items = vm.recentTabs()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (items.isEmpty()) {
                        onSidebar()
                        return@awaitEachGesture
                    }
                    recents.show(items)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        change.consume()
                        if (recents.hover(origin + change.position)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        if (!change.pressed) break
                    }
                    recents.release()?.let { vm.selectTab(it) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(zenIcon("sidebar"), "Spaces", Modifier.size(20.dp), tint = tint)
    }
}

class RecentsMenu {
    var items by mutableStateOf<List<TabItem>>(emptyList())
        private set
    var open by mutableStateOf(false)
        private set
    var hovered by mutableStateOf<String?>(null)
        private set
    val bounds = mutableMapOf<String, Rect>()

    fun show(list: List<TabItem>) {
        bounds.clear()
        items = list
        hovered = null
        open = true
    }

    /** Returns true when the finger moved onto a different tab. */
    fun hover(point: Offset): Boolean {
        val id = bounds.entries.firstOrNull { it.value.contains(point) }?.key
        val changed = id != null && id != hovered
        hovered = id
        return changed
    }

    fun release(): TabItem? {
        open = false
        return items.firstOrNull { it.id == hovered }
    }
}

@Composable
fun RecentsPopover(recents: RecentsMenu, modifier: Modifier) {
    AnimatedVisibility(
        recents.open,
        modifier = modifier.navigationBarsPadding().padding(start = 12.dp, bottom = BarHeight + BarMargin * 2),
        enter = scaleIn(spring(dampingRatio = 0.75f, stiffness = 500f), initialScale = 0.5f, transformOrigin = TransformOrigin(0.1f, 1f)) + fadeIn(tween(120)),
        exit = scaleOut(tween(140), targetScale = 0.7f, transformOrigin = TransformOrigin(0.1f, 1f)) + fadeOut(tween(140)),
    ) {
        Surface(
            Modifier.width(290.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.padding(6.dp)) {
                Text(
                    "Recent tabs",
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Newest sits at the bottom, right under the finger.
                recents.items.asReversed().forEach { tab ->
                    val hovered = recents.hovered == tab.id
                    val scale by animateFloatAsState(if (hovered) 1.03f else 1f, label = "recentScale")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent)
                            .onGloballyPositioned { recents.bounds[tab.id] = it.boundsInRoot() }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Favicon(tab.url, tab.icon, 18.dp)
                        Text(
                            tab.title,
                            Modifier.padding(start = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (hovered) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarMenu(vm: BrowserViewModel, expanded: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    @Composable
    fun Item(label: String, icon: String, action: () -> Unit) {
        DropdownMenuItem(
            text = { Text(label) },
            leadingIcon = { Icon(zenIcon(icon), null, Modifier.size(18.dp)) },
            onClick = {
                onDismiss()
                action()
            },
        )
    }
    DropdownMenu(expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(horizontal = 8.dp)) {
            IconButton(onClick = { onDismiss(); vm.goBack() }) { Icon(zenIcon("back"), "Back", Modifier.size(18.dp)) }
            IconButton(onClick = { onDismiss(); vm.goForward() }) { Icon(zenIcon("forward"), "Forward", Modifier.size(18.dp)) }
            IconButton(onClick = { onDismiss(); vm.reload() }) {
                Icon(zenIcon(if (vm.progress in 1..99) "close" else "reload"), "Reload", Modifier.size(18.dp))
            }
            IconButton(onClick = { onDismiss(); share(context, vm.url) }) { Icon(zenIcon("share"), "Share", Modifier.size(18.dp)) }
        }
        HorizontalDivider()
        Item("New tab", "plus") { vm.openLauncher() }
        Item("New private tab", "privateBrowsing") { vm.startPrivateBrowsing() }
        Item("Copy link", "link") {
            copyToClipboard(context, vm.url)
            vm.message = "Link copied"
        }
        Item("Find in page", "search-page") { vm.startFind() }
        Item(if (vm.isDesktopMode()) "Mobile site" else "Desktop site", "screen") { vm.toggleDesktopMode() }
        Item("Settings", "settings") { vm.showSettings = true }
    }
}

@Composable
private fun SuggestionsPanel(vm: BrowserViewModel, onDone: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = if (vm.privateMode) PrivateTint else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            vm.suggestions.forEach { suggestion ->
                SuggestionRow(suggestion) {
                    vm.pick(suggestion, inNewTab = false)
                    onDone()
                }
            }
        }
    }
}

/** One result row, styled like desktop's urlbar: "Title — url", "Switch to Tab →", or a search term. */
@Composable
fun SuggestionRow(suggestion: Suggestion, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (suggestion) {
            is Suggestion.OpenTab -> {
                Favicon(suggestion.tab.url, suggestion.tab.icon, 18.dp)
                Text(suggestion.tab.title, Modifier.weight(1f).padding(start = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Switch to Tab", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = muted)
                Box(
                    Modifier.padding(start = 8.dp).size(22.dp).clip(RoundedCornerShape(5.dp)).background(muted.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(zenIcon("arrow-right"), null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurface) }
            }
            is Suggestion.Visited -> {
                Favicon(suggestion.visit.url, null, 18.dp)
                Text(
                    buildAnnotatedString {
                        append(suggestion.visit.title)
                        withStyle(SpanStyle(color = muted)) { append(" — " + suggestion.visit.url.removePrefix("https://").removePrefix("www.")) }
                    },
                    Modifier.padding(start = 16.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            is Suggestion.Search -> {
                Icon(zenIcon("search-glass"), null, Modifier.size(16.dp), tint = muted)
                Text(suggestion.text, Modifier.padding(start = 18.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun FindBar(vm: BrowserViewModel, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    val onSurface = MaterialTheme.colorScheme.onSurface
    GlassSurface(modifier.padding(12.dp).fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp), solidity = 1f) {
        Row(Modifier.fillMaxSize().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = vm.findQuery.orEmpty(),
                onValueChange = { vm.find(it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardActions = KeyboardActions(onSearch = { vm.find(vm.findQuery.orEmpty()) }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (vm.findQuery.isNullOrEmpty()) Text("Find in page", color = onSurface.copy(alpha = 0.5f))
                        inner()
                    }
                },
            )
            Text(vm.findResult, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { vm.find(vm.findQuery.orEmpty(), backwards = true) }) { Icon(Icons.Default.KeyboardArrowUp, "Previous") }
            IconButton(onClick = { vm.find(vm.findQuery.orEmpty()) }) { Icon(Icons.Default.KeyboardArrowDown, "Next") }
            IconButton(onClick = vm::endFind) { Icon(zenIcon("close"), "Close find", Modifier.size(16.dp)) }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
