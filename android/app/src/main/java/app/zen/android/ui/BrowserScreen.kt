package app.zen.android.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zen.android.browser.PageColor
import app.zen.android.browser.UrlInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView

val PrivateTint = Color(0xFF3B2A5C)
private val BarHeight = 52.dp
private val BarMargin = 8.dp

@Composable
fun BrowserScreen(vm: BrowserViewModel, dark: Boolean) {
    val scope = rememberCoroutineScope()
    val drawer = remember { DrawerController(scope) }
    val spaces by vm.spaces.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val prefs by vm.settings.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(drawer.isOpen) { if (drawer.isOpen) vm.syncNow() }

    // Only claim "back" when the page has history, so Android 14's predictive back-to-home still plays otherwise.
    BackHandler(vm.canGoBack) { vm.goBack() }
    BackHandler(drawer.isOpen) { drawer.close() }
    BackHandler(editing) { editing = false }
    BackHandler(vm.findQuery != null) { vm.endFind() }
    BackHandler(vm.fullScreen) { vm.exitFullScreen() }

    val background = MaterialTheme.colorScheme.background
    val statusTarget = when {
        vm.privateMode -> PrivateTint
        prefs.tintStatusBar -> vm.pageTopColor ?: background
        else -> background
    }
    val statusColor by animateColorAsState(statusTarget, tween(250), label = "statusBar")
    val coveredByScreen = vm.showSettings || vm.loginSession != null
    SystemBars(lightIcons = !(if (coveredByScreen) background else statusColor).isLight(), hidden = vm.fullScreen)

    val actions = SidebarActions(
        onSpaceChange = { vm.activeSpace = it },
        onTab = { tab ->
            vm.selectTab(tab)
            drawer.close()
        },
        onCloseTab = vm::closeTab,
        onClearTabs = vm::clearRegularTabs,
        onMove = { vm.move(it) },
        onToggleFolder = vm.settings::toggleFolder,
        onNewTab = {
            drawer.close()
            editing = true
            vm.newTab()
        },
        onSignIn = {
            vm.beginLogin()
            drawer.close()
        },
        onSync = { vm.syncNow() },
        onSettings = {
            vm.showSettings = true
            drawer.close()
        },
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(statusColor)) {
        val drawerWidth = min(maxWidth * 0.86f, 380.dp)
        val widthPx = with(LocalDensity.current) { drawerWidth.toPx() }
        drawer.widthPx = widthPx

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = drawer.fraction.value * 56.dp.toPx()
                    val scale = 1f - drawer.fraction.value * 0.04f
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!vm.fullScreen) Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
                PageView(vm, prefs.tintStatusBar, Modifier.weight(1f))
            }

            if (!vm.fullScreen) {
                EdgeSwipe(drawer, Modifier.align(Alignment.CenterStart).offset(y = 80.dp))
                vm.findQuery?.let { FindBar(vm, Modifier.align(Alignment.TopCenter).statusBarsPadding()) }
                BottomChrome(
                    vm = vm,
                    editing = editing,
                    onEditingChange = { editing = it },
                    onSidebar = { drawer.open() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        if (drawer.fraction.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * drawer.fraction.value))
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { drawer.dragBy(it) },
                        onDragStopped = { drawer.settle(it) },
                    )
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { drawer.close() },
            )
            Box(
                Modifier
                    .width(drawerWidth)
                    .fillMaxHeight()
                    .offset { androidx.compose.ui.unit.IntOffset(((drawer.fraction.value - 1f) * widthPx).toInt(), 0) }
                    .clip(RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
                    .nestedScroll(drawer.nestedScroll)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { drawer.dragBy(it) },
                        onDragStopped = { drawer.settle(it) },
                    ),
            ) {
                if (vm.privateMode) {
                    PrivateSidebar(vm, onTab = actions.onTab, onClose = { drawer.close() })
                } else {
                    SpacesSidebar(
                        state = spaces,
                        status = status,
                        expandedFolders = prefs.expandedFolders,
                        activeSpace = vm.activeSpace,
                        activeTab = vm.activeTab,
                        dark = dark,
                        actions = actions,
                        onPrivate = {
                            vm.togglePrivateMode()
                        },
                    )
                }
            }
        }

        Overlays(vm)
    }
}

/** A drawer that tracks the finger, like the desktop sidebar peeking in. */
class DrawerController(private val scope: CoroutineScope) {
    val fraction = Animatable(0f)
    var widthPx = 1f
    val isOpen get() = fraction.targetValue == 1f

    fun open() = animateTo(1f)
    fun close() = animateTo(0f)

    fun dragBy(dx: Float) {
        scope.launch { fraction.snapTo((fraction.value + dx / widthPx).coerceIn(0f, 1f)) }
    }

    fun settle(velocity: Float) {
        val target = when {
            velocity > 900f -> 1f
            velocity < -900f -> 0f
            else -> if (fraction.value > 0.5f) 1f else 0f
        }
        animateTo(target, velocity / widthPx)
    }

    private fun animateTo(target: Float, velocity: Float = 0f) {
        scope.launch { fraction.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 500f), initialVelocity = velocity) }
    }

    /** Lets the space pager hand an over-scroll past its last page to the drawer, so swiping left closes it. */
    val nestedScroll = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.x < 0f && source == NestedScrollSource.UserInput) {
                dragBy(available.x)
                return Offset(available.x, 0f)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (fraction.value < 1f) settle(available.x)
            return available
        }
    }
}

@Composable
private fun SystemBars(lightIcons: Boolean, hidden: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !lightIcons
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun PageView(vm: BrowserViewModel, sampleColor: Boolean, modifier: Modifier) {
    var geckoView by remember { mutableStateOf<GeckoView?>(null) }
    val background = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val navBar = WindowInsets.navigationBars.getBottom(density)
    val barSpace = with(density) { (BarHeight + BarMargin * 2).roundToPx() } + navBar
    val shown by animateFloatAsState(if (vm.chromeVisible && !vm.fullScreen) 1f else 0f, tween(220), label = "chrome")

    AndroidView(
        modifier = modifier,
        factory = { context ->
            GeckoView(context).apply {
                coverUntilFirstPaint(background.toArgbInt())
                geckoView = this
            }
        },
        update = { view ->
            val session = vm.session
            if (session != null && view.session !== session) {
                view.coverUntilFirstPaint(background.toArgbInt())
                view.releaseSession()
                view.setSession(session)
            }
            // Gecko lays the page out above the bar, so short pages are never hidden behind it.
            view.setDynamicToolbarMaxHeight(if (vm.fullScreen) 0 else barSpace)
            // Gecko already lifts fixed elements by the max height; clipping only gives that space back while the bar hides.
            view.setVerticalClipping(-((1f - shown) * barSpace).toInt())
        },
    )

    LaunchedEffect(vm.captureRequest, sampleColor) {
        val view = geckoView ?: return@LaunchedEffect
        if (!sampleColor) return@LaunchedEffect
        delay(80)
        view.capturePixels().accept({ bitmap: Bitmap? ->
            if (bitmap == null) return@accept
            try {
                val readable = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
                val row = IntArray(readable.width)
                readable.getPixels(row, 0, readable.width, 0, 2.coerceAtMost(readable.height - 1), readable.width, 1)
                vm.pageTopColor = PageColor.dominant(row)?.let { Color(it) }
                if (readable !== bitmap) readable.recycle()
            } finally {
                bitmap.recycle()
            }
        }, { null })
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)

/** Android honours at most 200dp of back-gesture exclusion per screen edge; anything taller is ignored. */
private val EdgeSwipeHeight = 200.dp

@Composable
private fun EdgeSwipe(drawer: DrawerController, modifier: Modifier) {
    Box(
        modifier
            .width(24.dp)
            .height(EdgeSwipeHeight)
            .systemGestureExclusion()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { drawer.dragBy(it) },
                onDragStopped = { drawer.settle(it) },
            ),
    )
}

/** Frosted, translucent surface used by the floating bar and its panels. */
@Composable
fun GlassSurface(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(26.dp), tint: Color? = null, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val dark = !MaterialTheme.colorScheme.surface.isLight()
    val base = tint ?: MaterialTheme.colorScheme.surface
    val fill = Brush.verticalGradient(
        listOf(
            lerp(base, Color.White, if (dark) 0.06f else 0.4f).copy(alpha = 0.92f),
            base.copy(alpha = 0.86f),
        )
    )
    val rim = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.7f), Color.White.copy(alpha = if (dark) 0.04f else 0.15f))
    )
    Box(
        modifier
            .graphicsLayer {
                this.shape = shape
                shadowElevation = 18f
                clip = true
            }
            .background(fill)
            .border(0.8.dp, rim, shape),
    ) { content() }
}

@Composable
private fun BottomChrome(
    vm: BrowserViewModel,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSidebar: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        AnimatedVisibility(editing && vm.suggestions.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            SuggestionsPanel(vm) { onEditingChange(false) }
        }
        AnimatedVisibility(
            visible = vm.chromeVisible || editing,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            AddressBar(vm, editing, onEditingChange, onSidebar)
        }
    }
}

@Composable
private fun AddressBar(vm: BrowserViewModel, editing: Boolean, onEditingChange: (Boolean) -> Unit, onSidebar: () -> Unit) {
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

    GlassSurface(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = BarMargin).fillMaxWidth().height(BarHeight),
        tint = if (vm.privateMode) PrivateTint else null,
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSidebar) {
                val space = vm.space(vm.activeSpace)
                when {
                    vm.privateMode -> Icon(zenIcon("privateBrowsing"), "Private tabs", Modifier.size(20.dp), tint = Color.White)
                    space?.icon != null -> ZenIconView(space.icon, 20.dp, onSurface)
                    else -> Icon(zenIcon("sidebar"), "Spaces", Modifier.size(20.dp), tint = onSurface)
                }
            }

            if (editing) {
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
                IconButton(onClick = { if (field.text.isEmpty()) stopEditing() else field = TextFieldValue("") }) {
                    Icon(zenIcon("close"), "Clear", Modifier.size(16.dp), tint = onSurface)
                }
            } else {
                UrlLabel(
                    vm = vm,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(vm.activeTab) {
                            var total = Offset.Zero
                            detectDragGestures(
                                onDragStart = { total = Offset.Zero },
                                onDrag = { change, amount ->
                                    change.consume()
                                    total += amount
                                },
                                onDragEnd = {
                                    val threshold = 64.dp.toPx()
                                    val handled = when {
                                        total.y < -threshold && kotlin.math.abs(total.y) > kotlin.math.abs(total.x) -> {
                                            onEditingChange(true)
                                            vm.newTab()
                                            true
                                        }
                                        total.x > threshold -> vm.switchTab(-1)
                                        total.x < -threshold -> vm.switchTab(1)
                                        else -> false
                                    }
                                    if (handled) haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                },
                            )
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onEditingChange(true) },
                )
                IconButton(onClick = {
                    copyToClipboard(context, vm.url)
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    vm.message = "Link copied"
                }) {
                    Icon(zenIcon("edit-copy"), "Copy link", Modifier.size(18.dp), tint = onSurface)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(zenIcon("menu"), "More", Modifier.size(18.dp), tint = onSurface) }
                    BarMenu(vm, menu, onDismiss = { menu = false }, onNewTab = { onEditingChange(true) })
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

@Composable
private fun UrlLabel(vm: BrowserViewModel, modifier: Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        val webPage = vm.url.startsWith("http")
        if (webPage) {
            Icon(
                zenIcon(if (vm.secure) "security" else "security-broken"),
                if (vm.secure) "Secure connection" else "Not secure",
                Modifier.size(13.dp),
                tint = if (vm.secure) onSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = UrlInput.display(vm.url).ifEmpty { "Search or enter address" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BarMenu(vm: BrowserViewModel, expanded: Boolean, onDismiss: () -> Unit, onNewTab: () -> Unit) {
    val context = LocalContext.current
    fun item(label: String, icon: String, action: () -> Unit): @Composable () -> Unit = {
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
        item("New tab", "plus") {
            onNewTab()
            vm.newTab()
        }()
        item("New private tab", "privateBrowsing") { vm.newPrivateTab() }()
        item("Find in page", "search-page") { vm.startFind() }()
        item(if (vm.isDesktopMode()) "Mobile site" else "Desktop site", "screen") { vm.toggleDesktopMode() }()
        item("Settings", "settings") { vm.showSettings = true }()
    }
}

@Composable
private fun SuggestionsPanel(vm: BrowserViewModel, onDone: () -> Unit) {
    Surface(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (vm.privateMode) PrivateTint else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            vm.suggestions.forEach { suggestion ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (suggestion) {
                                is Suggestion.OpenTab -> vm.selectTab(suggestion.tab)
                                is Suggestion.Search -> vm.navigate(suggestion.text)
                            }
                            vm.clearSuggestions()
                            onDone()
                        }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (suggestion) {
                        is Suggestion.OpenTab -> {
                            Favicon(suggestion.tab.url, suggestion.tab.icon, 18.dp)
                            Text(suggestion.tab.title, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Switch to tab", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        is Suggestion.Search -> {
                            Icon(zenIcon("search-page"), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(suggestion.text, Modifier.padding(start = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindBar(vm: BrowserViewModel, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    val onSurface = MaterialTheme.colorScheme.onSurface
    GlassSurface(modifier.padding(12.dp).fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp)) {
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
            IconButton(onClick = { vm.find(vm.findQuery.orEmpty(), backwards = true) }) {
                Icon(Icons.Default.KeyboardArrowUp, "Previous")
            }
            IconButton(onClick = { vm.find(vm.findQuery.orEmpty()) }) { Icon(Icons.Default.KeyboardArrowDown, "Next") }
            IconButton(onClick = vm::endFind) { Icon(zenIcon("close"), "Close find", Modifier.size(16.dp)) }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun PrivateSidebar(vm: BrowserViewModel, onTab: (app.zen.android.sync.TabItem) -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PrivateTint, Color(0xFF1C1530))))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(zenIcon("privateBrowsing"), null, Modifier.size(20.dp), tint = Color.White)
            Text("Private", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                "Close all",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    vm.closePrivateTabs()
                    onClose()
                }.padding(10.dp),
            )
        }
        Text(
            "Private tabs never sync and are forgotten when you close them.",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )
        Column(Modifier.weight(1f).padding(8.dp)) {
            vm.privateTabs.forEach { tab ->
                androidx.compose.runtime.key(tab.id) {
                    PrivateTabRow(tab, tab.id == vm.activeTab, onClick = { onTab(tab) }, onCloseTab = { vm.closeTab(tab.id) })
                }
            }
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).clickable {
                    vm.newPrivateTab()
                    onClose()
                }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(zenIcon("plus"), null, Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.7f))
                Text("New private tab", Modifier.padding(start = 12.dp), color = Color.White.copy(alpha = 0.7f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showSettings = true; onClose() }) { Icon(zenIcon("settings"), "Settings", Modifier.size(18.dp), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            Text(
                "Back to spaces",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.togglePrivateMode() }.padding(12.dp),
            )
        }
    }
}

@Composable
private fun PrivateTabRow(tab: app.zen.android.sync.TabItem, selected: Boolean, onClick: () -> Unit, onCloseTab: () -> Unit) {
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState()
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { onCloseTab() },
        backgroundContent = {
            if (state.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.5f)))
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Favicon(tab.url, null, 18.dp)
            Text(tab.title, Modifier.padding(start = 12.dp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Link", text))
}

fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
