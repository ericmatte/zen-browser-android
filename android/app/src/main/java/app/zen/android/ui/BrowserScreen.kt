package app.zen.android.ui

import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zen.android.browser.EmptySelection
import app.zen.android.browser.PageColor
import app.zen.android.browser.PullAwareGeckoView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

val PrivateTint = Color(0xFF17171A)
val BarHeight = 52.dp
val BarMargin = 8.dp

@Composable
fun BrowserScreen(vm: BrowserViewModel, dark: Boolean) {
    val scope = rememberCoroutineScope()
    val drawer = remember { DrawerController(scope) }
    val spaces by vm.spaces.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val prefs by vm.settings.state.collectAsStateWithLifecycle()
    val menu = remember { BarMenuState() }
    val linkMenu = remember { SlideSelect() }
    val haptics = LocalHapticFeedback.current
    var pageOrigin by remember { mutableStateOf(Offset.Zero) }
    var editing by remember { mutableStateOf(false) }
    val recents = remember { RecentsMenu() }
    val pageShift = remember { Animatable(0f) }

    LaunchedEffect(drawer.isOpen) {
        if (drawer.isOpen) vm.syncNow() else vm.showLibrary = false
    }
    // History lives inside the sidebar, like desktop's library: opening it from the menu brings the sidebar along.
    LaunchedEffect(vm.showLibrary) { if (vm.showLibrary && !drawer.isOpen) drawer.open() }
    val library by animateFloatAsState(if (vm.showLibrary) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 380f), label = "library")
    val essentials by vm.essentials.collectAsStateWithLifecycle()
    // Private tabs always use the side opposite to spaces (desktop's zen.tabs.vertical.right-side picks the spaces side).
    val drawerOnRight = vm.privateMode != prefs.sidebarRight
    LaunchedEffect(drawerOnRight) { if (!drawer.isOpen) drawer.snapSide(drawerOnRight) }

    // Only claim "back" when the page has history, so Android 14's predictive back-to-home still plays otherwise.
    BackHandler(!vm.canGoBack && vm.hasPage && !vm.launcherOpen) { vm.openHomeFromBack() }
    BackHandler(vm.canGoBack) { vm.goBack() }
    BackHandler(drawer.isOpen) { drawer.close() }
    BackHandler(editing) { editing = false }
    BackHandler(vm.launcherOpen && !vm.launcherFromBack) { vm.closeLauncher() }
    BackHandler(vm.findQuery != null) { vm.endFind() }
    BackHandler(vm.fullScreen) { vm.exitFullScreen() }

    val background = MaterialTheme.colorScheme.background
    val statusTarget = when {
        vm.privateMode -> PrivateTint
        prefs.tintStatusBar -> vm.pageTopColor ?: background
        else -> background
    }
    val statusColor by animateColorAsState(statusTarget, tween(250), label = "statusBar")
    val coveredByScreen = vm.showSettings || vm.loginSession != null || vm.launcherOpen
    SystemBars(lightIcons = !(if (coveredByScreen) background else statusColor).isLight(), hidden = vm.fullScreen)

    fun enterPrivate() = scope.launch {
        val search = EmptySelection.privateEntryShowsSearch(vm.privateTabs.map { it.id })
        vm.togglePrivateMode()
        if (search) {
            vm.openLauncher()
            drawer.snapClosed()
            drawer.snapSide(!prefs.sidebarRight)
        } else {
            drawer.moveTo(right = !prefs.sidebarRight)
        }
    }

    // The private sidebar opens with no page behind it; closing it without a choice falls back to the latest private tab.
    LaunchedEffect(drawer.isOpen, vm.hasPage, vm.launcherOpen) {
        if (!drawer.isOpen && !vm.hasPage && !vm.launcherOpen && vm.ready) vm.showFallback()
    }

    fun leavePrivate() = scope.launch {
        vm.togglePrivateMode()
        drawer.moveTo(right = prefs.sidebarRight)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(statusColor)) {
        val drawerWidth = androidx.compose.ui.unit.lerp(min(maxWidth * 0.86f, 380.dp), maxWidth, library)
        val density = LocalDensity.current
        val widthPx = with(density) { drawerWidth.toPx() }
        val screenPx = with(density) { maxWidth.toPx() }
        drawer.widthPx = widthPx
        drawer.screenPx = screenPx
        val swipe = remember(screenPx) { PageSwipe(scope, pageShift, screenPx, vm::hasNeighbor, vm::switchTab) }
        val landscape = maxWidth > maxHeight
        LaunchedEffect(landscape) { vm.noteRotation() }
        val sideBar = landscape && !vm.fullScreen
        val barLift = BarHeight + BarMargin + barBottomMargin() + 6.dp
        val inset = maxWidth * BarSideInset

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val direction = 1f - 2f * drawer.side.value
                    translationX = drawer.fraction.value * 56.dp.toPx() * direction
                    val scale = 1f - drawer.fraction.value * 0.04f
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!vm.fullScreen) Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(background)
                        .onGloballyPositioned { pageOrigin = it.positionInRoot() }
                        .pointerInput(Unit) {
                            // Gecko opens the link menu while the finger is still down; from then on the finger can slide onto an entry.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                vm.notePageTouch()
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    if (vm.contextMenu != null) {
                                        linkMenu.reset()
                                        val moved = followHeldFinger(down.id, { pageOrigin }, linkMenu, haptics)
                                        val action = linkMenu.take()
                                        if (action != null) action() else if (moved) vm.contextMenu = null
                                        break
                                    }
                                }
                            }
                        }
                        .then(if (sideBar) Modifier.windowInsetsPadding(SideBarInsets).padding(start = BarHeight) else Modifier),
                ) {
                    PageView(vm, prefs.tintStatusBar, landscape, Modifier.fillMaxSize().graphicsLayer { translationX = pageShift.value })
                    if (!vm.hasPage) Box(Modifier.fillMaxSize().background(if (vm.privateMode) PrivateBackdrop else background))
                }
            }

            if (!vm.fullScreen) {
                LauncherBackdrop(vm)
                BottomChrome(
                    vm = vm,
                    editing = editing,
                    onEditingChange = { editing = it },
                    onSidebar = { drawer.open() },
                    recents = recents,
                    swipe = swipe,
                    menu = menu,
                    landscape = landscape,
                    edgeColor = statusColor,
                    modifier = Modifier,
                )
                val besideBar = Modifier.windowInsetsPadding(SideBarInsets).padding(start = BarHeight + 8.dp)
                RecentsPopover(
                    recents,
                    if (landscape) {
                        Modifier.align(Alignment.TopStart).statusBarsPadding().then(besideBar).padding(top = 8.dp)
                    } else {
                        Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = inset, bottom = barLift)
                    },
                )
                BarMenu(
                    vm,
                    menu,
                    landscape,
                    if (landscape) {
                        Modifier.align(Alignment.BottomStart).statusBarsPadding().navigationBarsPadding().then(besideBar).padding(top = 8.dp, bottom = 8.dp)
                    } else {
                        // The bubble's tail reaches down into the gap above the bar.
                        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = inset, bottom = barLift - 3.dp)
                    },
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
            val onRight = drawer.side.value > 0.5f
            val drawerShape = (22.dp * (1f - library)).let { r ->
                if (onRight) RoundedCornerShape(topStart = r, bottomStart = r) else RoundedCornerShape(topEnd = r, bottomEnd = r)
            }
            val edgeColor = if (vm.privateMode || dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.1f)
            Box(
                Modifier
                    .width(drawerWidth)
                    .fillMaxHeight()
                    .offset { IntOffset(drawer.x().toInt(), 0) }
                    .clip(drawerShape)
                    // A hairline along the edge facing the page; it fades out as the library takes the full width.
                    .drawWithContent {
                        drawContent()
                        val edge = 1.5.dp.toPx()
                        val corner = 22.dp.toPx()
                        val outline = drawerShape.createOutline(size, layoutDirection, this)
                        val left = if (onRight) 0f else size.width - corner - edge
                        clipRect(left = left, right = left + corner + edge) {
                            drawOutline(outline, edgeColor.copy(alpha = edgeColor.alpha * (1f - library)), style = Stroke(edge * 2))
                        }
                    }
                    .nestedScroll(drawer.nestedScroll)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { drawer.dragBy(it) },
                        onDragStopped = { drawer.settle(it) },
                    ),
            ) {
                if (library > 0f) {
                    val theme = vm.space(vm.activeSpace)?.theme
                    val spec = remember(theme, dark) { ZenGradient.spec(theme, dark) }
                    Box(Modifier.fillMaxSize().then(if (vm.privateMode) Modifier.background(PrivateBackdrop) else Modifier.zenSpaceBackground(spec)))
                }
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val scale = 1f - 0.08f * library
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - library
                    },
                ) {
                    if (vm.privateMode) {
                        PrivateSidebar(
                            vm,
                            onTab = { tab ->
                                vm.selectTab(tab)
                                drawer.close()
                            },
                            onClose = { drawer.close() },
                            onLeave = { leavePrivate() },
                        )
                    } else {
                        SpacesSidebar(
                            vm = vm,
                            spaces = spaces,
                            status = status,
                            prefs = prefs,
                            essentials = essentials,
                            dark = dark,
                            onClose = { drawer.close() },
                            onPrivate = { enterPrivate() },
                        )
                    }
                }
                if (library > 0f) {
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = -(1f - library) * size.width }) {
                        LibraryScreen(vm, dark, onOpened = { drawer.close() })
                    }
                }
            }
        }

        Overlays(vm, dark, linkMenu)
    }
}

/**
 * A drawer that tracks the finger, like the desktop sidebar peeking in. It
 * lives on the left for spaces and on the right for private tabs, so the two
 * modes never look alike.
 */
class DrawerController(private val scope: CoroutineScope) {
    val fraction = Animatable(0f)
    val side = Animatable(0f)
    var widthPx = 1f
    var screenPx = 1f
    val isOpen get() = fraction.targetValue == 1f
    private val onRight get() = side.targetValue == 1f

    fun open() = animateTo(1f)
    fun close() = animateTo(0f)

    fun x(): Float {
        val left = (fraction.value - 1f) * widthPx
        val right = screenPx - fraction.value * widthPx
        return left + (right - left) * side.value
    }

    fun dragBy(dx: Float) {
        val delta = if (onRight) -dx else dx
        scope.launch { fraction.snapTo((fraction.value + delta / widthPx).coerceIn(0f, 1f)) }
    }

    fun settle(velocity: Float) {
        val v = if (onRight) -velocity else velocity
        val target = when {
            v > 900f -> 1f
            v < -900f -> 0f
            else -> if (fraction.value > 0.5f) 1f else 0f
        }
        animateTo(target, v / widthPx)
    }

    suspend fun moveTo(right: Boolean) = side.animateTo(if (right) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 260f))

    suspend fun snapSide(right: Boolean) = side.snapTo(if (right) 1f else 0f)

    suspend fun snapClosed() = fraction.snapTo(0f)

    suspend fun closeAndWait() = fraction.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 500f))

    private fun animateTo(target: Float, velocity: Float = 0f) {
        scope.launch { fraction.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 500f), initialVelocity = velocity) }
    }

    /** Lets the space pager hand an over-scroll past its last page to the drawer, so swiping left closes it. */
    val nestedScroll = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!onRight && available.x < 0f && source == NestedScrollSource.UserInput) {
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

/**
 * Swiping the address bar drags the whole page; past a threshold the page
 * slides out and the neighbouring tab slides in from the other side.
 */
class PageSwipe(
    private val scope: CoroutineScope,
    val shift: Animatable<Float, *>,
    val widthPx: Float,
    private val hasNeighbor: (Int) -> Boolean,
    private val switch: (Int) -> Boolean,
) {
    /** True while the new tab's page slides in; its identity is already settled in the bar by then. */
    var incoming by mutableStateOf(false)
        private set

    private fun stepFor(dx: Float) = if (dx > 0) -1 else 1

    fun drag(dx: Float) {
        scope.launch {
            val next = shift.value + dx
            val resisted = if (next != 0f && !hasNeighbor(stepFor(next))) shift.value + dx * 0.25f else next
            shift.snapTo(resisted)
        }
    }

    fun release(velocity: Float) {
        scope.launch {
            val offset = shift.value
            val step = stepFor(offset)
            val committed = (abs(offset) > widthPx * 0.28f || abs(velocity) > 1200f && velocity * offset > 0) && hasNeighbor(step)
            if (!committed) {
                shift.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                return@launch
            }
            val out = if (offset > 0) widthPx else -widthPx
            shift.animateTo(out, tween(160))
            incoming = true
            switch(step)
            shift.snapTo(-out)
            shift.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 380f))
            incoming = false
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageView(vm: BrowserViewModel, sampleColor: Boolean, landscape: Boolean, modifier: Modifier) {
    var geckoView by remember { mutableStateOf<PullAwareGeckoView?>(null) }
    val background = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val navBar = WindowInsets.navigationBars.getBottom(density)
    // In landscape the bar rests beside the page, so Gecko keeps no room for it at the bottom.
    val barSpace = if (landscape) 0 else with(density) { (BarHeight + BarMargin + barBottomMargin()).roundToPx() } + navBar
    val keyboard = WindowInsets.isImeVisible
    val shown by animateFloatAsState(if (vm.chromeVisible && !vm.fullScreen && !keyboard) 1f else 0f, tween(220), label = "chrome")

    val pull = remember { PullState() }
    Box(modifier.pullToRefresh(pull, { !vm.fullScreen && geckoView?.canPull == true }, vm::refresh)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PullAwareGeckoView(context).apply {
                    // Lets Android autofill services (1Password, Bitwarden, Google…) read and fill login forms.
                    setAutofillEnabled(true)
                    importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_YES
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
                } else if (session == null && view.session != null) {
                    view.releaseSession()
                }
                // Gecko lays the page out above the bar, so short pages are never hidden behind it.
                view.setDynamicToolbarMaxHeight(if (vm.fullScreen) 0 else barSpace)
                // Gecko already lifts fixed elements by the max height; clipping only gives that space back while the bar hides.
                view.setVerticalClipping(-((1f - shown) * barSpace).toInt())
            },
        )
        PullIndicator(pull, vm.loading, Modifier.align(Alignment.TopCenter))
    }

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

/**
 * Gesture navigation (the floating handle) has no tappable bottom inset; its
 * handle already sits apart from the content, so the bar drops its bottom gap.
 */
@Composable
fun barBottomMargin(): androidx.compose.ui.unit.Dp {
    val density = LocalDensity.current
    val gestureNav = WindowInsets.tappableElement.getBottom(density) == 0 && WindowInsets.navigationBars.getBottom(density) > 0
    return if (gestureNav) 0.dp else BarMargin
}

fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)

/** The colour at the top edge of the glass bar, shared by panels that join it. */
@Composable
fun glassTopColor(tint: Color?): Color {
    val base = tint ?: MaterialTheme.colorScheme.surface
    return lerp(base, Color.White, if (base.isLight()) 0.4f else 0.06f)
}

/** Frosted, translucent surface used by the floating bar. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(26.dp),
    tint: Color? = null,
    solidity: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = !MaterialTheme.colorScheme.surface.isLight()
    val base = tint ?: MaterialTheme.colorScheme.surface
    val top = 0.92f + 0.08f * solidity
    val bottom = 0.86f + 0.14f * solidity
    val fill = Brush.verticalGradient(listOf(glassTopColor(tint).copy(alpha = top), base.copy(alpha = bottom)))
    val rim = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.7f), Color.White.copy(alpha = if (dark) 0.04f else 0.15f))
    )
    Box(
        modifier
            .graphicsLayer {
                this.shape = shape
                shadowElevation = 18f * (1f - solidity)
                clip = true
            }
            .background(fill)
            .border(0.8.dp, rim, shape),
    ) { content() }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Link", text))
}

fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
