package app.zen.android.ui

import app.zen.android.browser.UrlInput
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import app.zen.android.browser.DragGap
import app.zen.android.browser.SpaceHighlight
import kotlinx.coroutines.delay
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.zen.android.sync.FolderItem
import app.zen.android.sync.LocalEdit
import app.zen.android.sync.SidebarItem
import app.zen.android.sync.Space
import app.zen.android.sync.SpacesState
import app.zen.android.sync.SyncStatus
import app.zen.android.sync.TabItem
import app.zen.android.sync.flattenTabs
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** True when the sidebar text is light, i.e. desktop's zen-should-be-dark-mode. */
val LocalLightChrome = compositionLocalOf { true }

/** An opaque colour close to the gradient behind the rows, for rows that slide. */
val LocalRowBackdrop = compositionLocalOf { Color.Black }

/** vertical-tabs.css: --tab-background-color-selected. */
@Composable
fun selectedTabBackground(): Color = if (LocalLightChrome.current) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.85f)

/** zen-theme.css: --zen-toolbar-element-bg. */
@Composable
fun toolbarElementBackground(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = if (LocalLightChrome.current) 0.15f else 0.08f)

@Composable
fun SpacesSidebar(
    vm: BrowserViewModel,
    spaces: List<Space>,
    status: SyncStatus,
    prefs: Preferences,
    essentials: List<TabItem>,
    dark: Boolean,
    onClose: () -> Unit,
    onPrivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = spaces.indexOfFirst { it.uuid == vm.activeSpace }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initial) { spaces.size }
    val scope = rememberCoroutineScope()
    val gestures = remember { SidebarGestures() }
    var renaming by remember { mutableStateOf<FolderItem?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pager.settledPage, spaces.size) {
        spaces.getOrNull(pager.settledPage)?.let { vm.activeSpace = it.uuid }
    }
    LaunchedEffect(vm.activeSpace) {
        val index = spaces.indexOfFirst { it.uuid == vm.activeSpace }
        if (index >= 0 && index != pager.currentPage) pager.animateScrollToPage(index)
    }

    // Colours follow the finger: the two neighbouring spaces are blended by the pager offset.
    val last = (spaces.size - 1).coerceAtLeast(0)
    val position = if (spaces.isEmpty()) 0f else (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, last.toFloat())
    val from = floor(position).toInt()
    val to = (from + 1).coerceAtMost(last)
    val t = position - from
    val themeFrom = spaces.getOrNull(from)?.theme
    val themeTo = spaces.getOrNull(to)?.theme
    val specFrom = remember(themeFrom, dark) { ZenGradient.spec(themeFrom, dark) }
    val specTo = remember(themeTo, dark) { ZenGradient.spec(themeTo, dark) }
    val chromeFrom = remember(themeFrom, dark) { ZenGradient.chrome(themeFrom, dark) }
    val chromeTo = remember(themeTo, dark) { ZenGradient.chrome(themeTo, dark) }
    val text = lerp(Color(chromeFrom.text), Color(chromeTo.text), t)
    val accent = lerp(Color(chromeFrom.accent), Color(chromeTo.accent), t)
    val lightChrome = if (t < 0.5f) chromeFrom.lightText else chromeTo.lightText
    val backdrop = Color(specFrom.stops[specFrom.stops.size / 2].second)
    val sidebarColors = MaterialTheme.colorScheme.copy(
        onSurface = text,
        onBackground = text,
        onSurfaceVariant = text.copy(alpha = text.alpha * 0.72f),
        primary = accent,
        onPrimary = if (accent.isLight()) Color.Black else Color.White,
    )

    val focusManager = LocalFocusManager.current
    val spaceMenu = remember { HoldMenuState() }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                gestures.sidebarRect = it.boundsInRoot()
            }
            .pointerInput(Unit) {
                // A touch anywhere but the space name being edited ends the rename, like clicking away on desktop.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val title = gestures.editingTitle
                    if (title != null && !title.contains(origin + down.position)) focusManager.clearFocus()
                }
            }
            .closesReveal(gestures.reveal),
    ) {
        Box(Modifier.matchParentSize().zenSpaceBackground(specFrom))
        if (t > 0.001f && to != from) Box(Modifier.matchParentSize().graphicsLayer { alpha = t }.zenSpaceBackground(specTo))

        MaterialTheme(colorScheme = sidebarColors, typography = MaterialTheme.typography) {
            CompositionLocalProvider(LocalLightChrome provides lightChrome, LocalRowBackdrop provides backdrop) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    EssentialsGrid(vm, essentials, gestures, onClose)

                    if (spaces.isEmpty()) {
                        EmptySpaces(status, vm::beginLogin, Modifier.weight(1f))
                    } else {
                        BoxWithConstraints(Modifier.weight(1f)) {
                            val maxTemporary = maxHeight * 0.6f
                            Column(Modifier.fillMaxSize()) {
                                HorizontalPager(state = pager, modifier = Modifier.weight(1f), key = { spaces[it].uuid }, beyondViewportPageCount = 1) { page ->
                                    SpacePage(vm, spaces[page], page == pager.currentPage, prefs, gestures, onClose, onRenameFolder = { renaming = it })
                                }
                                val current = spaces[pager.currentPage.coerceIn(0, spaces.lastIndex)]
                                TemporaryTabs(vm, current, prefs, gestures, onClose, onRenameFolder = { renaming = it }, Modifier.fillMaxWidth().heightIn(max = maxTemporary))
                            }
                        }
                    }

                    SidebarFooter(
                        spaces = spaces,
                        position = position,
                        status = status,
                        onPick = { scope.launch { pager.animateScrollToPage(it) } },
                        onLibrary = { vm.showLibrary = true },
                        spaceMenu = spaceMenu,
                        menuFor = { i ->
                            val space = spaces[i]
                            listOfNotNull(
                                MenuAction("edit", "Rename Space") {
                                    scope.launch { pager.animateScrollToPage(i) }
                                    gestures.renameSpace = space.uuid
                                },
                                MenuAction("plus", "New Tab") {
                                    vm.activeSpace = space.uuid
                                    onClose()
                                    vm.openLauncher()
                                },
                                MenuAction("pin", if (space.uuid in prefs.collapsedSpaces) "Show Pinned Tabs" else "Hide Pinned Tabs") {
                                    vm.settings.toggleSpaceCollapsed(space.uuid)
                                },
                                MenuAction("trash", "Clear Temporary Tabs", destructive = true) { vm.clearRegularTabs() }
                                    .takeIf { space.regularTabs.isNotEmpty() },
                            )
                        },
                        onPrivate = onPrivate,
                        onRetry = { vm.syncNow() },
                    )
                }
                HoldOverlay(gestures, origin)
                HoldMenuHost(spaceMenu)
            }
        }
    }

    renaming?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename folder") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameFolder(folder.id, name)
                    renaming = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

/** What the finger is holding: a row of the space, a folder, or an essential. */
sealed interface Held {
    val key: String
    val bounds: Rect
}

data class HeldTab(val tab: TabItem, val spaceUuid: String, override val bounds: Rect) : Held {
    override val key get() = tab.id
}

data class HeldFolder(val folder: FolderItem, val spaceUuid: String, override val bounds: Rect) : Held {
    override val key get() = folder.id
}

data class HeldEssential(val tab: TabItem, override val bounds: Rect) : Held {
    override val key get() = tab.id
}

/**
 * The iOS-style hold: after a long press a menu appears; sliding onto an
 * entry and lifting the finger runs it. Moving away from the menu instead
 * picks the item up so it can be dropped elsewhere (reorder, essentials).
 */
class SidebarGestures {
    var held by mutableStateOf<Held?>(null)
        private set
    var actions by mutableStateOf<List<MenuAction>>(emptyList())
        private set
    var menuOpen by mutableStateOf(false)
        private set
    val select = SlideSelect()
    var dragging by mutableStateOf(false)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var grab = Offset.Zero
        private set
    var renameSpace by mutableStateOf<String?>(null)
    /** The landing gap of a drag, shared by the space's pinned list and the fixed temporary list. */
    internal var gap by mutableStateOf<DropGap?>(null)
    var temporaryRect = Rect.Zero
    val reveal = RevealGroup()
    var editingTitle: Rect? = null
    var overEssentials by mutableStateOf(false)
    var essentialsRect = Rect.Zero
    var listRect = Rect.Zero
    var sidebarRect = Rect.Zero
    var menuRect = Rect.Zero
    val hitRects = mutableMapOf<String, Pair<Rect, Held>>()
    val rowRects = mutableMapOf<String, Rect>()
    private var start = Offset.Zero
    private var enteredMenu = false
    private var moved = false

    fun hit(point: Offset): Held? =
        hitRects.values.filter { it.first.contains(point) }.minByOrNull { it.first.height * it.first.width }?.let { (rect, h) ->
            when (h) {
                is HeldTab -> h.copy(bounds = rect)
                is HeldFolder -> h.copy(bounds = rect)
                is HeldEssential -> h.copy(bounds = rect)
            }
        }

    fun begin(target: Held, menu: List<MenuAction>, point: Offset) {
        held = target
        actions = menu
        select.reset()
        menuRect = Rect.Zero
        menuOpen = menu.isNotEmpty()
        dragging = false
        enteredMenu = false
        moved = false
        start = point
        pointer = point
        grab = point - target.bounds.topLeft
    }

    /** Returns true when the hovered menu entry changed, for a haptic tick. */
    fun move(point: Offset, slop: Float): Boolean {
        pointer = point
        if ((point - start).getDistance() > slop / 3) moved = true
        if (dragging) {
            overEssentials = held !is HeldEssential && essentialsRect.contains(point)
            return false
        }
        val inMenu = menuOpen && menuRect.contains(point)
        if (inMenu) enteredMenu = true
        val changed = menuOpen && select.hover(point)
        if (!inMenu && !enteredMenu && (point - start).getDistance() > slop) {
            menuOpen = false
            dragging = true
        }
        return changed
    }

    /** Ends the gesture; a still finger leaves the menu open for taps. */
    fun release(): (() -> Unit)? {
        val action = select.take()
        if (dragging || action != null || moved || !menuOpen) closeAll()
        return action
    }

    fun closeAll() {
        held = null
        menuOpen = false
        dragging = false
        select.reset()
        overEssentials = false
    }
}

private fun tabMenu(vm: BrowserViewModel, tab: TabItem, spaceUuid: String, context: android.content.Context): List<MenuAction> = listOfNotNull(
    MenuAction(if (tab.pinned) "unpin" else "pin", if (tab.pinned) "Unpin Tab" else "Pin Tab") { vm.setPinned(tab, !tab.pinned, spaceUuid) },
    MenuAction("essential-add", "Add to Essentials") { vm.addEssential(tab) }.takeUnless { vm.essentialsFull() },
    MenuAction("link", "Copy Link") {
        copyToClipboard(context, tab.url)
        vm.message = "Link copied"
    },
    MenuAction("share", "Share") { share(context, tab.url) },
    MenuAction("close", "Close Tab", destructive = true) { vm.closeTab(tab.id) },
)

@Composable
private fun EssentialsGrid(vm: BrowserViewModel, essentials: List<TabItem>, gestures: SidebarGestures, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val slop = with(LocalDensity.current) { 28.dp.toPx() }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    val dropTarget = gestures.dragging && gestures.held !is HeldEssential
    val highlight by animateFloatAsState(if (dropTarget && gestures.overEssentials) 1f else 0f, label = "essentialsDrop")

    Box(
        Modifier
            .padding(start = 10.dp, end = 10.dp, top = 10.dp)
            .onGloballyPositioned {
                gridOrigin = it.positionInRoot()
                gestures.essentialsRect = it.boundsInRoot()
            }
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = highlight), RoundedCornerShape(14.dp))
            .pointerInput(essentials) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val long = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    val target = gestures.hit(gridOrigin + long.position) as? HeldEssential ?: return@awaitEachGesture
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    gestures.begin(
                        target,
                        listOf(
                            MenuAction("essential-remove", "Remove from Essentials", destructive = true) { vm.removeEssential(target.tab.id) },
                            MenuAction("link", "Copy Link") {
                                copyToClipboard(context, target.tab.url)
                                vm.message = "Link copied"
                            },
                            MenuAction("share", "Share") { share(context, target.tab.url) },
                        ),
                        gridOrigin + long.position,
                    )
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (gestures.move(gridOrigin + change.position, slop)) haptics.performHapticFeedback(MenuHoverTick)
                        if (!change.pressed) break
                    }
                    val dropInList = gestures.dragging && gestures.listRect.contains(gestures.pointer)
                    gestures.release()?.invoke()
                    if (dropInList) vm.activeSpace?.let { vm.essentialToSpace(target.tab, it) }
                }
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (essentials.isEmpty()) {
                AnimatedVisibility(dropTarget, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Box(Modifier.fillMaxWidth().height(46.dp).background(toolbarElementBackground()), contentAlignment = Alignment.Center) {
                        Text("Drop here to add to Essentials", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            essentials.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { tab ->
                        EssentialTile(
                            tab,
                            selected = tab.id == vm.activeTab,
                            dimmed = gestures.dragging && gestures.held?.key == tab.id,
                            modifier = Modifier.weight(1f).onGloballyPositioned {
                                gestures.hitRects[tab.id] = it.boundsInRoot() to HeldEssential(tab, it.boundsInRoot())
                            },
                        ) {
                            vm.selectTab(tab)
                            onClose()
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun EssentialTile(tab: TabItem, selected: Boolean, dimmed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ring = rememberFaviconAccent(tab.url, tab.icon) ?: MaterialTheme.colorScheme.primary
    Box(
        modifier
            .height(46.dp)
            .graphicsLayer { alpha = if (dimmed) 0.35f else 1f }
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) selectedTabBackground() else toolbarElementBackground())
            .border(if (selected) 2.dp else 0.dp, if (selected) ring else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Favicon(tab.url, tab.icon, 20.dp) }
}

private sealed interface Row {
    val key: String
}

private data class ItemRow(val item: SidebarItem) : Row {
    override val key get() = item.id
}

private data object DividerRow : Row {
    override val key = "__divider"
}

internal val SidebarRowHeight = 46.dp
internal val SidebarIconSize = 20.dp
private val SidebarFolderIconSize = 24.dp
internal val SidebarIconGap = 12.dp

@Composable
internal fun sidebarText() = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)

private data object NewTabRow : Row {
    override val key = "__newtab"
}

private fun rowsOf(space: Space, collapsed: Boolean): List<Row> =
    (if (collapsed) emptyList() else space.pinnedItems.map(::ItemRow)) + DividerRow + space.regularTabs.map(::ItemRow) + NewTabRow

private fun itemOf(held: Held): SidebarItem = when (held) {
    is HeldTab -> held.tab
    is HeldFolder -> held.folder
    is HeldEssential -> held.tab
}

/** A gap opened at [target] (index among the rows without [heldKey]) for a dragged row [height] px tall. */
internal data class DropGap(val heldKey: String, val from: Int, val target: Int, val height: Float)

/** Where a dragged row would land: the edit to send, and its index among the other rows. */
private fun dropTarget(space: Space, rows: List<Row>, gestures: SidebarGestures, held: Held, y: Float): Pair<LocalEdit.MoveItem?, Int> {
    val ordered = rows.filter { it.key != held.key }
    val rects = ordered.map { gestures.rowRects[it.key] }
    var index = ordered.size
    for (i in ordered.indices) {
        val r = rects[i] ?: continue
        if (y < r.center.y) {
            index = i
            break
        }
    }
    val divider = ordered.indexOf(DividerRow)
    val newTab = ordered.indexOf(NewTabRow)
    // Folders always live in the pinned section, and "New Tab" always closes the list, under the thumb.
    if (held is HeldFolder && index > divider) index = divider
    if (index > newTab) index = newTab
    val moved = ordered.toMutableList().apply { add(index.coerceAtMost(size), ItemRow(itemOf(held))) }
    if (moved.map { it.key } == rows.map { it.key }) return null to index
    val pinned = moved.take(moved.indexOf(DividerRow)).map { it.key }
    val regular = moved.subList(moved.indexOf(DividerRow) + 1, moved.indexOf(NewTabRow)).map { it.key }
    val hiddenPinned = space.pinnedItems.map { it.id }.filter { id -> rows.none { it.key == id } }
    return LocalEdit.placing(space.uuid, held.key, hiddenPinned + pinned, regular) to index
}

@Composable
private fun SpacePage(
    vm: BrowserViewModel,
    space: Space,
    isCurrent: Boolean,
    prefs: Preferences,
    gestures: SidebarGestures,
    onClose: () -> Unit,
    onRenameFolder: (FolderItem) -> Unit,
) {
    val collapsed = space.uuid in prefs.collapsedSpaces
    val rows = remember(space, collapsed) { rowsOf(space, collapsed) }
    val listState = rememberLazyListState()
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    val motion = rememberRowMotion(prefs, gestures)

    // Pulling the list down from its top asks the server for desktop's latest changes right away.
    val pull = remember { PullState() }
    val syncing = vm.status.collectAsStateWithLifecycle().value == SyncStatus.Syncing
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
    Box(Modifier.fillMaxSize().pullToRefresh(pull, { atTop && gestures.held == null }, { vm.syncNow() })) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    listOrigin = it.positionInRoot()
                    // The pager keeps a neighbour page composed; only the visible one owns the drop area.
                    if (isCurrent) gestures.listRect = it.boundsInRoot()
                }
                .sidebarHold(vm, space, rows, prefs, gestures, listState, { listOrigin }, onRenameFolder),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
        ) {
            item(key = "__header") {
                SpaceHeader(space, collapsed, gestures, onToggle = { vm.settings.toggleSpaceCollapsed(space.uuid) }, onRename = { vm.renameSpace(space.uuid, it) })
            }
            items(rows.take(rows.indexOf(DividerRow)), key = { it.key }) { row -> SidebarRow(vm, row, rows, space, prefs, gestures, motion, onClose) }
        }
        PullIndicator(pull, syncing, Modifier.align(Alignment.TopCenter))
    }
}

/**
 * The phone's temporary tabs are one list shared by every space, so they sit
 * below the pager and stay put while the spaces above slide by.
 */
@Composable
private fun TemporaryTabs(
    vm: BrowserViewModel,
    space: Space,
    prefs: Preferences,
    gestures: SidebarGestures,
    onClose: () -> Unit,
    onRenameFolder: (FolderItem) -> Unit,
    modifier: Modifier,
) {
    val collapsed = space.uuid in prefs.collapsedSpaces
    val rows = remember(space, collapsed) { rowsOf(space, collapsed) }
    val listState = rememberLazyListState()
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    val motion = rememberRowMotion(prefs, gestures)
    LaunchedEffect(rows) { gestures.gap = null }
    // New tabs join at the bottom, next to the thumb; keep them in view.
    LaunchedEffect(space.regularTabs.size) { listState.scrollToItem((rows.size - rows.indexOf(DividerRow) - 1).coerceAtLeast(0)) }
    LazyColumn(
        state = listState,
        modifier = modifier
            .onGloballyPositioned {
                listOrigin = it.positionInRoot()
                gestures.temporaryRect = it.boundsInRoot()
            }
            .sidebarHold(vm, space, rows, prefs, gestures, listState, { listOrigin }, onRenameFolder),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp),
    ) {
        items(rows.drop(rows.indexOf(DividerRow)), key = { it.key }) { row -> SidebarRow(vm, row, rows, space, prefs, gestures, motion, onClose) }
    }
}

/** How rows move: no entrance animation on first layout, and no placement spring while folders or drags move them by hand. */
private class RowMotion(val settled: Boolean, val quietPlacement: Boolean)

@Composable
private fun rememberRowMotion(prefs: Preferences, gestures: SidebarGestures): RowMotion {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        settled = true
    }
    // Kept after the drop until the moved rows arrive from the store, so nothing jumps back meanwhile.
    LaunchedEffect(gestures.gap != null, gestures.dragging) {
        if (gestures.gap != null && !gestures.dragging) {
            delay(1200)
            gestures.gap = null
        }
    }
    // Folders resize their own item and drags shift rows by hand; a placement spring on top would make
    // rows trail behind at their own pace, so it only plays for other changes (a tab closing, a sync).
    var quietPlacement by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.expandedFolders, gestures.dragging, gestures.gap) {
        quietPlacement = true
        delay(400)
        quietPlacement = gestures.dragging || gestures.gap != null
    }
    return RowMotion(settled, quietPlacement)
}

@Composable
private fun LazyItemScope.SidebarRow(
    vm: BrowserViewModel,
    row: Row,
    rows: List<Row>,
    space: Space,
    prefs: Preferences,
    gestures: SidebarGestures,
    motion: RowMotion,
    onClose: () -> Unit,
) {
    val gap = gestures.gap
    val lifted = (gestures.held?.key == row.key && gestures.dragging) || gap?.heldKey == row.key
    val shift = gap?.let { g ->
        if (row.key == g.heldKey) return@let 0f
        val index = rows.filter { it.key != g.heldKey }.indexOf(row)
        DragGap.slots(index, g.from, g.target) * g.height
    } ?: 0f
    val offset by animateFloatAsState(shift, if (gap != null) spring(dampingRatio = 0.9f, stiffness = 700f) else snap(), label = "gap")
    val modifier = Modifier
        .then(if (motion.settled) Modifier.animateItem(placementSpec = if (motion.quietPlacement) null else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)) else Modifier)
        .onGloballyPositioned { gestures.rowRects[row.key] = it.boundsInRoot() }
        .graphicsLayer {
            translationY = offset
            alpha = if (!motion.settled || lifted) 0f else 1f
        }
    when (row) {
        is ItemRow -> Box(modifier) {
            when (val item = row.item) {
                is TabItem -> SwipeTab(vm, item, space.uuid, depth = 0, gestures = gestures, onOpen = onClose)
                is FolderItem -> FolderEntry(vm, item, space.uuid, prefs, gestures, onClose)
            }
        }
        DividerRow -> Row(modifier.fillMaxWidth().height(28.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))
            if (space.regularTabs.isNotEmpty()) {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).clickable { vm.clearRegularTabs() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(zenIcon("dart-down"), null, Modifier.size(14.dp), tint = muted)
                    Text("Clear", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = muted)
                }
            }
        }
        // Laid out exactly like a tab row, so its icon and label line up with the tabs above.
        NewTabRow -> Row(
            modifier.fillMaxWidth().height(SidebarRowHeight).clip(RoundedCornerShape(10.dp)).clickable {
                onClose()
                vm.openLauncher()
            }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(SidebarIconSize), contentAlignment = Alignment.Center) {
                Icon(zenIcon("plus"), null, Modifier.size(SidebarIconSize - 2.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("New Tab", Modifier.padding(start = SidebarIconGap), style = sidebarText(), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * The hold gesture of a sidebar list: a long press opens the item's menu;
 * sliding away picks it up, and the rows of both lists open a gap where it
 * would land. [rows] is the whole column (pinned items, separator, temporary tabs).
 */
private fun Modifier.sidebarHold(
    vm: BrowserViewModel,
    space: Space,
    rows: List<Row>,
    prefs: Preferences,
    gestures: SidebarGestures,
    listState: androidx.compose.foundation.lazy.LazyListState,
    origin: () -> Offset,
    onRenameFolder: (FolderItem) -> Unit,
): Modifier = composed {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val slop = with(LocalDensity.current) { 28.dp.toPx() }
    pointerInput(space, rows, prefs.expandedFolders) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val long = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            val target = gestures.hit(origin() + long.position) ?: return@awaitEachGesture
            if (target is HeldEssential) return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val menu = when (target) {
                is HeldTab -> tabMenu(vm, target.tab, space.uuid, context)
                is HeldFolder -> listOf(
                    MenuAction("folder", if (target.folder.id in prefs.expandedFolders) "Collapse Folder" else "Expand Folder") {
                        vm.settings.toggleFolder(target.folder.id)
                    },
                    MenuAction("edit", "Rename Folder") { onRenameFolder(target.folder) },
                )
                is HeldEssential -> emptyList()
            }
            gestures.begin(target, menu, origin() + long.position)
            var edit: LocalEdit.MoveItem? = null
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                val point = origin() + change.position
                if (gestures.move(point, slop)) haptics.performHapticFeedback(MenuHoverTick)
                if (gestures.dragging) {
                    val own = Rect(origin(), listState.layoutInfo.viewportSize.let { androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) })
                    val edge = 48.dp.toPx()
                    if (point.y < own.top + edge && point.y > own.top) listState.dispatchRawDelta(-14f)
                    if (point.y > own.bottom - edge && point.y < own.bottom) listState.dispatchRawDelta(14f)
                    val inside = gestures.listRect.contains(point) || gestures.temporaryRect.contains(point)
                    if (inside && !gestures.overEssentials) {
                        val (next, index) = dropTarget(space, rows, gestures, target, point.y)
                        edit = next
                        val height = gestures.rowRects[target.key]?.takeIf { rows.any { it.key == target.key } }?.height ?: target.bounds.height
                        gestures.gap = DropGap(target.key, rows.indexOfFirst { it.key == target.key }, index, height)
                    } else {
                        edit = null
                        gestures.gap = null
                    }
                }
                if (!change.pressed) break
            }
            val toEssentials = gestures.dragging && gestures.overEssentials
            if (edit == null || toEssentials) gestures.gap = null
            val chosen = gestures.release()
            when {
                chosen != null -> chosen()
                toEssentials && target is HeldTab -> vm.addEssential(target.tab)
                edit != null -> vm.move(edit!!)
            }
        }
    }
}

/** Tap the name to rename the space (synced); tap the icon or chevron to fold its pinned tabs away. */
@Composable
private fun SpaceHeader(space: Space, collapsed: Boolean, gestures: SidebarGestures, onToggle: () -> Unit, onRename: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(gestures.renameSpace) {
        if (gestures.renameSpace == space.uuid) {
            editing = true
            gestures.renameSpace = null
        }
    }
    var field by remember(space.name) { mutableStateOf(TextFieldValue(space.name)) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val chevron by animateFloatAsState(if (collapsed) 0f else 90f, tween(300, easing = FolderEasingCurve), label = "spaceChevron")
    val onSurface = MaterialTheme.colorScheme.onSurface

    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
            ZenIconView(space.icon, 18.dp, onSurface) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(onSurface))
            }
        }
        if (editing) {
            fun commit() {
                if (!editing) return
                editing = false
                focused = false
                gestures.editingTitle = null
                focusManager.clearFocus()
                if (field.text.isNotBlank() && field.text != space.name) onRename(field.text)
            }
            LaunchedEffect(Unit) {
                field = TextFieldValue(space.name, TextRange(0, space.name.length))
                focus.requestFocus()
            }
            BasicTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = onSurface, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                cursorBrush = SolidColor(onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
                    .focusRequester(focus)
                    .onGloballyPositioned { gestures.editingTitle = it.boundsInRoot() }
                    .onFocusChanged {
                        if (it.isFocused) focused = true else if (focused) commit()
                    },
            )
        } else {
            Text(
                space.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).clickable { editing = true },
            )
        }
        IconButton(onClick = onToggle) {
            Icon(Icons.Default.KeyboardArrowRight, if (collapsed) "Show pinned tabs" else "Hide pinned tabs", Modifier.rotate(chevron), tint = onSurface)
        }
    }
}

@Composable
private fun FolderEntry(
    vm: BrowserViewModel,
    folder: FolderItem,
    spaceUuid: String,
    prefs: Preferences,
    gestures: SidebarGestures,
    onClose: () -> Unit,
    depth: Int = 0,
) {
    val expanded = folder.id in prefs.expandedFolders
    val containsActive = folder.items.any { child -> child.flattenTabs().any { it.id == vm.activeTab } }
    // One timing for the icon, the chevron and the rows, so the whole folder opens as one piece.
    val chevron by animateFloatAsState(if (expanded) 90f else 0f, tween(300, easing = FolderEasingCurve), label = "chevron")
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(SidebarRowHeight)
                .onGloballyPositioned { gestures.hitRects[folder.id] = it.boundsInRoot() to HeldFolder(folder, spaceUuid, it.boundsInRoot()) }
                .clip(RoundedCornerShape(10.dp))
                .clickable { vm.settings.toggleFolder(folder.id) }
                .padding(start = (10 + depth * 14).dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolderIcon(folder.icon, open = expanded, active = !expanded && containsActive, size = SidebarFolderIconSize)
            Text(
                folder.name,
                Modifier.weight(1f).padding(start = SidebarIconGap - (SidebarFolderIconSize - SidebarIconSize)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = sidebarText(),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(Icons.Default.KeyboardArrowRight, if (expanded) "Collapse" else "Expand", Modifier.size(20.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurface)
        }
        folder.items.forEach { child ->
            val active = child.flattenTabs().any { it.id == vm.activeTab }
            AnimatedVisibility(
                expanded || active,
                enter = expandVertically(tween(300, easing = FolderEasingCurve)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300, easing = FolderEasingCurve)) + fadeOut(tween(200)),
            ) {
                when (child) {
                    is TabItem -> SwipeTab(vm, child, spaceUuid, depth + 1, gestures, onClose)
                    is FolderItem -> FolderEntry(vm, child, spaceUuid, prefs, gestures, onClose, depth + 1)
                }
            }
        }
    }
}

/**
 * Left-to-right pins a temporary tab or unpins a pinned one. Right-to-left
 * unpins a pinned tab; on a temporary tab it uncovers desktop's archive button.
 */
@Composable
private fun SwipeTab(vm: BrowserViewModel, tab: TabItem, spaceUuid: String, depth: Int, gestures: SidebarGestures, onOpen: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    SwipeRow(
        key = tab.id,
        group = gestures.reveal,
        reveal = if (tab.pinned) null else SwipeAction("archive", "Archive tab", DestructiveRed) { vm.closeTab(tab.id) },
        commitStart = SwipeAction(if (tab.pinned) "unpin" else "pin", if (tab.pinned) "Unpin" else "Pin", accent) { vm.setPinned(tab, !tab.pinned, spaceUuid) },
        commitEnd = if (tab.pinned) SwipeAction("unpin", "Unpin", accent) { vm.setPinned(tab, false, spaceUuid) } else null,
        modifier = Modifier.onGloballyPositioned { gestures.hitRects[tab.id] = it.boundsInRoot() to HeldTab(tab, spaceUuid, it.boundsInRoot()) },
    ) {
        TabRow(tab, depth, tab.id == vm.activeTab) {
            vm.selectTab(tab)
            onOpen()
        }
    }
}

@Composable
private fun TabRow(tab: TabItem, depth: Int, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) selectedTabBackground() else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .height(SidebarRowHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = (10 + depth * 14).dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Favicon(tab.url, tab.icon, SidebarIconSize)
        Text(
            tab.title.ifBlank { UrlInput.display(tab.url) },
            Modifier.padding(start = SidebarIconGap),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = sidebarText(),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

private fun Color.over(under: Color): Color {
    val a = alpha + under.alpha * (1 - alpha)
    if (a == 0f) return Color.Transparent
    fun ch(c: Float, u: Float) = (c * alpha + u * under.alpha * (1 - alpha)) / a
    return Color(ch(red, under.red), ch(green, under.green), ch(blue, under.blue), a)
}

/** The lifted preview, the hold menu and the drop line, drawn above the sidebar. */
@Composable
private fun BoxScope.HoldOverlay(gestures: SidebarGestures, origin: Offset) {
    val held = gestures.held ?: return
    val density = LocalDensity.current
    val backdrop = LocalRowBackdrop.current
    val menuVisible = gestures.menuOpen

    if (menuVisible) {
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { gestures.closeAll() },
        )
    }

    val local = if (gestures.dragging) gestures.pointer - gestures.grab - origin else held.bounds.topLeft - origin
    val lift by animateFloatAsState(if (gestures.dragging || menuVisible) 1.03f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "lift")
    Box(
        Modifier
            .zIndex(2f)
            .offset { IntOffset(local.x.roundToInt(), local.y.roundToInt()) }
            .size(with(density) { held.bounds.width.toDp() }, with(density) { held.bounds.height.toDp() })
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
                shadowElevation = 14f
                shape = RoundedCornerShape(10.dp)
                clip = true
            }
            .background(selectedTabBackground().over(backdrop)),
    ) { HeldPreview(held) }

    var menuHeight by remember { mutableIntStateOf(0) }
    val gap = with(density) { 6.dp.toPx() }
    val below = held.bounds.bottom - origin.y + gap
    val limit = gestures.sidebarRect.bottom - origin.y - with(density) { 56.dp.toPx() }
    val fitsBelow = below + menuHeight <= limit
    val menuY = if (fitsBelow) below else held.bounds.top - origin.y - menuHeight - gap
    val menuX = (held.bounds.left - origin.x + with(density) { 8.dp.toPx() }).coerceAtLeast(0f)
    val pivot = TransformOrigin(0.1f, if (fitsBelow) 0f else 1f)
    AnimatedVisibility(
        menuVisible,
        modifier = Modifier.zIndex(3f).offset { IntOffset(menuX.roundToInt(), menuY.roundToInt()) },
        enter = scaleIn(spring(dampingRatio = 0.72f, stiffness = 520f), initialScale = 0.6f, transformOrigin = pivot) + fadeIn(tween(120)),
        exit = scaleOut(tween(140), targetScale = 0.8f, transformOrigin = pivot) + fadeOut(tween(140)),
    ) {
        MenuCard(
            gestures.actions,
            gestures.select,
            Modifier.onGloballyPositioned {
                menuHeight = it.size.height
                gestures.menuRect = it.boundsInRoot()
            },
        ) { action ->
            gestures.closeAll()
            action.run()
        }
    }
}

@Composable
private fun HeldPreview(held: Held) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    when (held) {
        is HeldEssential -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Favicon(held.tab.url, held.tab.icon, 20.dp) }
        is HeldTab -> Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Favicon(held.tab.url, held.tab.icon, SidebarIconSize)
            Text(held.tab.title, Modifier.padding(start = SidebarIconGap), maxLines = 1, overflow = TextOverflow.Ellipsis, color = onSurface, style = sidebarText())
        }
        is HeldFolder -> Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            FolderIcon(held.folder.icon, open = false, active = false, size = SidebarFolderIconSize)
            Text(held.folder.name, Modifier.padding(start = SidebarIconGap - (SidebarFolderIconSize - SidebarIconSize)), maxLines = 1, overflow = TextOverflow.Ellipsis, color = onSurface, style = sidebarText())
        }
    }
}

@Composable
private fun SidebarFooter(
    spaces: List<Space>,
    position: Float,
    status: SyncStatus,
    onPick: (Int) -> Unit,
    onLibrary: () -> Unit,
    spaceMenu: HoldMenuState,
    menuFor: (Int) -> List<MenuAction>,
    onPrivate: () -> Unit,
    onRetry: () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onLibrary) { Icon(zenIcon("library"), "Library", Modifier.size(20.dp), tint = tint) }
        val dots = rememberLazyListState()
        val highlight = toolbarElementBackground()
        LazyRow(
            Modifier.weight(1f).drawBehind {
                // One highlight slides between the icons with the pager, so it moves with the colours and pages.
                val items = dots.layoutInfo.visibleItemsInfo
                val x = SpaceHighlight.x(position) { i -> items.firstOrNull { it.index == i }?.offset } ?: return@drawBehind
                val side = items.first().size.toFloat()
                drawRoundRect(highlight, Offset(x, (size.height - side) / 2), Size(side, side), CornerRadius(SpaceDotCorner.toPx()))
            },
            state = dots,
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(spaces.size) { i ->
                val selection = (1f - abs(position - i)).coerceIn(0f, 1f)
                SpaceDot(spaces[i], selection, Modifier.holdMenu(spaceMenu) { menuFor(i) }) { onPick(i) }
            }
        }
        // Sync runs on its own, like desktop; only a failure shows up here.
        if (status is SyncStatus.Failed) {
            IconButton(onClick = onRetry) { Icon(Icons.Default.Warning, "Sync failed: ${status.message}", Modifier.size(20.dp), tint = Color(0xFFFFB347)) }
        }
        IconButton(onClick = onPrivate) { Icon(zenIcon("privateBrowsing"), "Private tabs", Modifier.size(20.dp), tint = tint) }
    }
}

private val SpaceDotCorner = 10.dp

/** [selection] goes from 0 to 1 as the pager reaches this space, so the icon brightens with the swipe. */
@Composable
private fun SpaceDot(space: Space, selection: Float, modifier: Modifier, onClick: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val tint = onSurface.copy(alpha = onSurface.alpha * (0.5f + 0.5f * selection))
    Box(
        modifier
            .size(34.dp)
            .clip(RoundedCornerShape(SpaceDotCorner))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ZenIconView(space.icon, 16.dp, tint) {
            Box(Modifier.size(6.dp + 2.dp * selection).clip(RoundedCornerShape(50)).background(tint))
        }
    }
}

@Composable
private fun EmptySpaces(status: SyncStatus, onSignIn: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZenLogo(Modifier.size(56.dp))
        Spacer(Modifier.height(18.dp))
        if (status == SyncStatus.SignedOut) {
            Text("Your spaces live here", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Sign in with your Mozilla account to bring your Zen spaces, pinned tabs and folders from desktop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                textAlign = TextAlign.Center,
            )
            Button(onClick = onSignIn) { Text("Sign in") }
        } else {
            Text(
                "No spaces yet. Turn on “Sync your sidebar across devices” in Zen desktop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ZenLogo(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    androidx.compose.foundation.Canvas(modifier) {
        val c = center
        val unit = size.minDimension / 600f
        listOf(300f to 54f, 200f to 38.5f, 115f to 23f).forEach { (r, w) ->
            drawCircle(color, radius = (r - w / 2) * unit, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * unit))
        }
    }
}
