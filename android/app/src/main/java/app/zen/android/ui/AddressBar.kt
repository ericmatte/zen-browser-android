package app.zen.android.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import app.zen.android.browser.NativeApp
import app.zen.android.browser.AppLinks
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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

/** How far the suggestions panel reaches down behind the bar. */
val SuggestionsOverlap = 28.dp

/** Height of the bar while typing: a little taller than at rest, like desktop's open urlbar. */
val EditBarHeight = 64.dp

/** At rest the bar leaves this share of the screen width free on each side. */
const val BarSideInset = 0.05f

/** Left-edge insets (cutout, landscape navigation bar) the vertical bar sits over. */
val SideBarInsets: WindowInsets
    @Composable get() = WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Start)

/**
 * The floating bar. Editing the URL and the new-tab search are the same bar
 * morphing into its docked, taller form above the keyboard. In landscape it
 * rests as a vertical strip on the left edge and only the docked form slides
 * up while typing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BottomChrome(
    vm: BrowserViewModel,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSidebar: () -> Unit,
    recents: RecentsMenu,
    swipe: PageSwipe,
    menu: BarMenuState,
    landscape: Boolean,
    edgeColor: Color,
    modifier: Modifier,
) {
    val searching = vm.launcherOpen
    val finding = vm.findQuery != null
    val typing = editing || searching
    val active = typing || finding
    val focusManager = LocalFocusManager.current

    fun finish() {
        onEditingChange(false)
        vm.closeLauncher()
        vm.clearSuggestions()
        focusManager.clearFocus()
    }

    // Hiding the keyboard (back gesture, keyboard button) is how a phone says "blur".
    val imeVisible = WindowInsets.isImeVisible
    var imeSeen by remember { mutableStateOf(false) }
    LaunchedEffect(typing, imeVisible) {
        when {
            !typing -> imeSeen = false
            imeVisible -> imeSeen = true
            imeSeen -> {
                imeSeen = false
                finish()
            }
        }
    }

    // A web page's own text field took the keyboard: the bar steps aside with it. The bar's own keyboard,
    // still sliding away after editing ends, must not count, or the bar would blink out and back.
    val barKeyboard = remember { BooleanArray(1) }
    if (active && imeVisible) barKeyboard[0] = true
    if (!imeVisible) barKeyboard[0] = false
    val webKeyboard = imeVisible && !active && !barKeyboard[0]
    // Docks only once the keyboard is really up, so the bar never drops to the screen edge while waiting for it.
    val dock by animateFloatAsState(if (active && imeVisible) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 420f), label = "dock")
    Box(modifier.fillMaxSize()) {
        if (editing && !searching) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() })
        }
        if (landscape) {
            AnimatedVisibility(
                visible = !active && !webKeyboard,
                modifier = Modifier.align(Alignment.CenterStart),
                enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { -it },
                exit = slideOutHorizontally(tween(160)) { -it },
            ) {
                SideBar(vm, edgeColor, onEdit = { onEditingChange(true) }, onSidebar, recents, menu)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                typing && vm.suggestions.isNotEmpty(),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                // Same width as the bar under it, whether that bar floats or is docked.
                SuggestionsPanel(vm, inNewTab = searching, Modifier.fillMaxWidth(1f - 2 * BarSideInset * (1f - (if (landscape) 1f else dock)))) { onEditingChange(false) }
            }
            AnimatedVisibility(
                visible = active || (!landscape && vm.chromeVisible && !webKeyboard),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                AddressBar(vm, typing, searching, finding, if (landscape) 1f else dock, ::finish, { onEditingChange(true) }, onSidebar, recents, swipe, menu)
            }
        }
    }
}

@Composable
private fun AddressBar(
    vm: BrowserViewModel,
    editing: Boolean,
    searching: Boolean,
    finding: Boolean,
    dock: Float,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
    onSidebar: () -> Unit,
    recents: RecentsMenu,
    swipe: PageSwipe,
    menu: BarMenuState,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    fun done() {
        vm.clearSuggestions()
        focusManager.clearFocus()
        onCancel()
    }

    val top = lerp(BarMargin, 0.dp, dock)
    val bottom = lerp(barBottomMargin(), 0.dp, dock)
    val topCorner = lerp(26.dp, 14.dp, dock)
    val bottomCorner = lerp(26.dp, 0.dp, dock)

    GlassSurface(
        modifier = Modifier
            .padding(top = top, bottom = bottom)
            .fillMaxWidth(1f - 2 * BarSideInset * (1f - dock))
            .height(lerp(BarHeight, EditBarHeight, dock)),
        shape = RoundedCornerShape(topStart = topCorner, topEnd = topCorner, bottomStart = bottomCorner, bottomEnd = bottomCorner),
        tint = if (vm.privateMode) PrivateTint else null,
        solidity = dock,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = lerp(0.dp, 6.dp, dock)), verticalAlignment = Alignment.CenterVertically) {
            if (finding) {
                FindContent(vm)
            } else if (editing) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(zenIcon("search-glass"), null, Modifier.size(18.dp), tint = onSurface.copy(alpha = 0.7f))
                }
                LaunchedEffect(searching) {
                    field = if (searching) TextFieldValue("") else TextFieldValue(vm.url, TextRange(0, vm.url.length))
                    if (!vm.launcherFromBack) focus.requestFocus()
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
                        if (searching) vm.launch(field.text) else vm.navigate(field.text)
                        done()
                    }),
                    modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { if (it.isFocused) vm.searchFieldFocused() },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (field.text.isEmpty()) Text(if (searching) "Search…" else "Search or enter address", color = onSurface.copy(alpha = 0.5f))
                            inner()
                        }
                    },
                )
                if (!searching && vm.hasPage) {
                    IconButton(modifier = Modifier.pressScale(), onClick = {
                        copyToClipboard(context, vm.url)
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        vm.message = "Link copied"
                        done()
                    }) {
                        Icon(zenIcon("link"), "Copy link", Modifier.size(18.dp), tint = onSurface)
                    }
                }
                IconButton(modifier = Modifier.pressScale(), onClick = {
                    if (field.text.isEmpty()) {
                        done()
                    } else {
                        field = TextFieldValue("")
                        vm.suggest("")
                    }
                }) {
                    Icon(zenIcon("close"), "Clear", Modifier.size(16.dp), tint = onSurface)
                }
            } else {
                SidebarButton(vm, onSidebar, recents)
                SwipingIdentity(
                    vm,
                    swipe,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pageSwipe(vm, swipe)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onEdit() },
                )
                NewTabButton(vm)
                MenuButton(menu, badge = vm.update != null)
            }
        }
    }
}

/**
 * Landscape resting bar: a full-height strip on the left edge, painted like
 * the status bar (the page's own colour), title running sideways and icons
 * upright. Its content turns light or dark to stay readable on that colour.
 */
@Composable
private fun SideBar(vm: BrowserViewModel, color: Color, onEdit: () -> Unit, onSidebar: () -> Unit, recents: RecentsMenu, menu: BarMenuState) {
    val content = if (color.isLight()) Color(0xFF1A1A1E) else Color.White
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onSurface = content)) {
        Column(
            Modifier
                .fillMaxHeight()
                .background(color)
                .windowInsetsPadding(SideBarInsets)
                .statusBarsPadding()
                .navigationBarsPadding()
                .width(BarHeight)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SidebarButton(vm, onSidebar, recents)
            if (vm.hasPage) SiteIdentity(vm.url, vm.secure, Modifier.padding(bottom = 6.dp), vm.loading, vm.reloading)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onEdit() },
                contentAlignment = Alignment.Center,
            ) {
                TitleLabel(vm.title.ifBlank { UrlInput.display(vm.url) }, Modifier.sideways())
            }
            NewTabButton(vm)
            MenuButton(menu, badge = vm.update != null, landscape = true)
        }
    }
}

/** Lays the content out rotated a quarter turn counter-clockwise, so it reads bottom to top. */
private fun Modifier.sideways(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(minWidth = constraints.minHeight, maxWidth = constraints.maxHeight, minHeight = constraints.minWidth, maxHeight = constraints.maxWidth),
    )
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(-(placeable.width - placeable.height) / 2, (placeable.width - placeable.height) / 2) { rotationZ = -90f }
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

/**
 * Favicon and title of the tab, riding along with the page while the bar is
 * swiped: the neighbour tab's identity slides in from the side it comes from.
 */
@Composable
private fun SwipingIdentity(vm: BrowserViewModel, swipe: PageSwipe, modifier: Modifier) {
    var slot by remember { mutableIntStateOf(1) }
    val shift = if (swipe.incoming) 0f else swipe.shift.value
    val offset = shift * slot / swipe.widthPx
    val neighbor = when {
        shift > 0f -> vm.neighbor(-1)?.let { it to offset - slot }
        shift < 0f -> vm.neighbor(1)?.let { it to offset + slot }
        else -> null
    }
    Box(modifier.clipToBounds().onSizeChanged { slot = it.width.coerceAtLeast(1) }, contentAlignment = Alignment.CenterStart) {
        Identity(
            vm.url,
            vm.secure,
            vm.title.ifBlank { UrlInput.display(vm.url) }.ifEmpty { "Search or enter address" },
            showIcon = vm.hasPage,
            loading = vm.loading,
            spinning = vm.reloading,
            modifier = Modifier.graphicsLayer {
                translationX = offset
                alpha = 1f - (kotlin.math.abs(offset) / slot).coerceIn(0f, 1f)
            },
        )
        neighbor?.let { (tab, x) ->
            Identity(
                tab.url,
                secure = !tab.url.startsWith("http:"),
                tab.title.ifBlank { UrlInput.display(tab.url) },
                showIcon = true,
                modifier = Modifier.graphicsLayer {
                    translationX = x
                    alpha = 1f - (kotlin.math.abs(x) / slot).coerceIn(0f, 1f)
                },
            )
        }
    }
}

@Composable
private fun Identity(url: String, secure: Boolean, title: String, showIcon: Boolean, modifier: Modifier, loading: Boolean = false, spinning: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showIcon) SiteIdentity(url, secure, Modifier.padding(end = 7.dp), loading, spinning)
        TitleLabel(title, Modifier)
    }
}

@Composable
private fun TitleLabel(title: String, modifier: Modifier) {
    Text(
        text = title,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

val SecureGreen = Color(0xFF2EB872)

/** The page's favicon with a small lock: green and closed on HTTPS, red and open otherwise. */
@Composable
private fun SiteIdentity(url: String, secure: Boolean, modifier: Modifier = Modifier, loading: Boolean = false, spinning: Boolean = false) {
    val web = url.startsWith("http")
    val safe = !web || secure
    Box(modifier.size(24.dp)) {
        Favicon(url, null, 20.dp, loading = loading, spinning = spinning)
        if (web) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(13.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (safe) SecureGreen else MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center,
            ) {
                Icon(zenIcon(if (safe) "security" else "security-broken"), if (safe) "Secure" else "Not secure", Modifier.size(8.dp), tint = Color.White)
            }
        }
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
            .size(BarButtonWidth, 48.dp)
            .pressScale()
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
                        if (recents.hover(origin + change.position)) haptics.performHapticFeedback(MenuHoverTick)
                        if (!change.pressed) break
                    }
                    recents.release()?.let { vm.selectTab(it) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(zenIcon(if (vm.privateMode) "privateBrowsing" else "sidebar"), if (vm.privateMode) "Private tabs" else "Spaces", Modifier.size(20.dp), tint = tint)
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
        modifier = modifier,
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
private fun AppIcon(app: NativeApp) {
    val bitmap = remember(app.packageName) { app.icon?.toBitmap(64, 64)?.asImageBitmap() }
    if (bitmap != null) androidx.compose.foundation.Image(bitmap, null, Modifier.size(20.dp))
}

/** Bar buttons keep a 48dp-tall touch target but sit closer together than Material's 48dp squares. */
private val BarButtonWidth = 42.dp

/** The landscape strip's "⋯" centre, above the menu's bottom (strip padding 4dp + half a button, minus the menu's 8dp lift). */
private val MenuTailFromBottom = 20.dp

class BarMenuState {
    var open by mutableStateOf(false)
    val select = SlideSelect()
}

/** "+" opens the new-tab search; holding it names it, like any icon-only button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTabButton(vm: BrowserViewModel) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above, 8.dp),
        tooltip = { PlainTooltip { Text("New tab") } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = vm::openLauncher, modifier = Modifier.size(BarButtonWidth, 48.dp).pressScale()) {
            Icon(zenIcon("plus"), "New tab", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Tap toggles the "⋯" menu; holding it opens the menu and the finger can slide onto an entry, like iOS. */
@Composable
private fun MenuButton(menu: BarMenuState, badge: Boolean, landscape: Boolean = false) {
    val haptics = LocalHapticFeedback.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val open by animateFloatAsState(if (menu.open) 1f else 0f, tween(160), label = "menuOpen")
    // In the floating bar the highlight sits in its rounded end, so its right corners share the bar's centre.
    val highlight = if (landscape) RoundedCornerShape(12.dp) else RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = BarHeight / 2 - 5.dp, bottomEnd = BarHeight / 2 - 5.dp)
    Box(
        Modifier
            .size(if (landscape) 48.dp else BarButtonWidth + 2.dp, 48.dp)
            .pressScale()
            .padding(start = 3.dp, end = 5.dp, top = 3.dp, bottom = 3.dp)
            .clip(highlight)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f * open))
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(menu) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var lifted = false
                    val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        val up = waitForUpOrCancellation()
                        lifted = true
                        up != null
                    }
                    if (lifted) {
                        if (tapped == true) menu.open = !menu.open
                        return@awaitEachGesture
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu.select.reset()
                    menu.open = true
                    val moved = followHeldFinger(down.id, { origin }, menu.select, haptics)
                    val action = menu.select.take()
                    if (action != null) action() else if (moved) menu.open = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(zenIcon("menu"), if (badge) "More, update available" else "More", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        if (badge) UpdateDot(Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp))
    }
}

/** The "⋯" menu, anchored just above the bar (the system dropdown keeps a 48dp gap from screen edges). */
@Composable
fun BarMenu(vm: BrowserViewModel, menu: BarMenuState, landscape: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    fun close() {
        menu.open = false
    }
    if (menu.open) {
        androidx.activity.compose.BackHandler { close() }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        )
    }
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    var nativeApp by remember { mutableStateOf<NativeApp?>(null) }
    LaunchedEffect(menu.open, vm.url) {
        if (menu.open) nativeApp = withContext(Dispatchers.Default) { if (vm.hasPage) AppLinks.find(context, vm.url) else null }
    }
    val bubble = if (landscape) BubbleShape(TailSide.Start, MenuTailFromBottom) else BubbleShape(TailSide.Bottom, (BarButtonWidth + 2.dp) / 2)
    AnimatedVisibility(
        menu.open,
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 1000f), initialScale = 0.7f, transformOrigin = TransformOrigin(0.9f, 1f)) + fadeIn(tween(80)),
        exit = scaleOut(tween(90), targetScale = 0.85f, transformOrigin = TransformOrigin(0.9f, 1f)) + fadeOut(tween(90)),
    ) {
        Surface(
            Modifier.width(250.dp),
            shape = bubble,
            color = if (vm.privateMode) PrivateTint else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (vm.privateMode) Color.White else MaterialTheme.colorScheme.onSurface,
            shadowElevation = 14.dp,
        ) {
            Column(Modifier.padding(bubble.contentPadding).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                // A disabled entry is dimmed, ignores taps and slides, and leaves the menu open.
                @Composable
                fun Nav(icon: String, label: String, enabled: Boolean = true, action: () -> Unit) {
                    val run = {
                        close()
                        action()
                    }
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .then(if (enabled) Modifier.slideTarget(menu.select, label, run) else Modifier)
                            .background(if (enabled && menu.select.hovered == label) highlight else Color.Transparent)
                            // Still swallows the tap when disabled, so it never reaches the backdrop that closes the menu.
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = if (enabled) LocalIndication.current else null,
                            ) { if (enabled) run() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(zenIcon(icon), label, Modifier.size(18.dp).graphicsLayer { alpha = if (enabled) 1f else 0.3f })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Nav("back", "Back", enabled = vm.canGoBack) { vm.goBack() }
                    Nav("forward", "Forward", enabled = vm.canGoForward) { vm.goForward() }
                    Nav(if (vm.loading) "close" else "reload", if (vm.loading) "Stop" else "Reload", enabled = vm.hasPage) { vm.reload() }
                    Nav("share", "Share", enabled = vm.hasPage) { share(context, vm.url) }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                @Composable
                fun Item(label: String, icon: String, leading: (@Composable () -> Unit)? = null, badge: Boolean = false, action: () -> Unit) {
                    val run = {
                        close()
                        action()
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .slideTarget(menu.select, label, run)
                            .background(if (menu.select.hovered == label) highlight else Color.Transparent)
                            .clickable(onClick = run)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (leading != null) Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { leading() } else Icon(zenIcon(icon), null, Modifier.size(18.dp))
                        Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                        if (badge) UpdateDot(Modifier.padding(start = 8.dp))
                    }
                }
                nativeApp?.let { app ->
                    Item("Open in ${app.label}", "", leading = { AppIcon(app) }) {
                        if (!AppLinks.open(context, vm.url, app)) vm.message = "${app.label} could not open this page"
                    }
                }
                Item("New private tab", "privateBrowsing") { vm.startPrivateBrowsing() }
                Item("Find in page", "search-page") { vm.startFind() }
                Item(if (vm.isDesktopMode()) "Mobile site" else "Desktop site", "screen") { vm.toggleDesktopMode() }
                Item("Settings", "settings", badge = vm.update != null) { vm.showSettings = true }
            }
        }
    }
}

@Composable
private fun SuggestionsPanel(vm: BrowserViewModel, inNewTab: Boolean, modifier: Modifier, onDone: () -> Unit) {
    // The panel runs on under the bar, in the bar's own colour, so the two read as one sheet while the
    // bar keeps its rounded corners.
    Surface(
        modifier.offset(y = SuggestionsOverlap),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = glassTopColor(if (vm.privateMode) PrivateTint else null),
        contentColor = if (vm.privateMode) Color.White else MaterialTheme.colorScheme.onSurface,
        shadowElevation = 12.dp,
    ) {
        // Best match sits at the bottom, right above the bar and the thumb.
        val reveal = remember { RevealGroup() }
        Column(Modifier.closesReveal(reveal).padding(top = 6.dp, bottom = 6.dp + SuggestionsOverlap)) {
            vm.suggestions.asReversed().forEach { suggestion ->
                key(suggestion.dismissKey) {
                    SwipeRow(
                        key = suggestion.dismissKey,
                        group = reveal,
                        reveal = SwipeAction("trash", "Remove suggestion", DestructiveRed) { vm.dismissSuggestion(suggestion) },
                        commitStart = null,
                        commitEnd = null,
                    ) {
                        SuggestionRow(suggestion) {
                            vm.pick(suggestion, inNewTab)
                            onDone()
                        }
                    }
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
                Icon(zenIcon(if (suggestion.recent) "history" else "search-glass"), null, Modifier.size(16.dp), tint = muted)
                Text(suggestion.text, Modifier.padding(start = 18.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Find in page: the bar itself turns into the find field, with the match count and arrows. */
@Composable
private fun RowScope.FindContent(vm: BrowserViewModel) {
    val focus = remember { FocusRequester() }
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Icon(zenIcon("search-page"), null, Modifier.size(18.dp), tint = onSurface)
    }
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
    Text(vm.findResult, style = MaterialTheme.typography.labelMedium, color = onSurface.copy(alpha = 0.7f))
    IconButton(modifier = Modifier.pressScale(), onClick = { vm.find(vm.findQuery.orEmpty(), backwards = true) }) { Icon(Icons.Default.KeyboardArrowUp, "Previous", tint = onSurface) }
    IconButton(modifier = Modifier.pressScale(), onClick = { vm.find(vm.findQuery.orEmpty()) }) { Icon(Icons.Default.KeyboardArrowDown, "Next", tint = onSurface) }
    IconButton(modifier = Modifier.pressScale(), onClick = vm::endFind) { Icon(zenIcon("close"), "Close find", Modifier.size(16.dp), tint = onSurface) }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
