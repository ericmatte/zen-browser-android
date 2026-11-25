package app.zen.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
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
    state: SpacesState,
    status: SyncStatus,
    prefs: Preferences,
    essentials: List<TabItem>,
    dark: Boolean,
    onClose: () -> Unit,
    onPrivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spaces = state.spaces()
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
                // A touch anywhere but an uncovered archive button slides that tab back, and does nothing else.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val point = origin + down.position
                    val title = gestures.editingTitle
                    if (title != null && !title.contains(point)) focusManager.clearFocus()
                    if (gestures.revealedTab != null && !gestures.archiveRect.contains(point)) {
                        gestures.revealedTab = null
                        down.consume()
                    }
                }
            },
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
                        HorizontalPager(state = pager, modifier = Modifier.weight(1f), key = { spaces[it].uuid }, beyondViewportPageCount = 1) { page ->
                            SpacePage(vm, spaces[page], page == pager.currentPage, prefs, gestures, onClose, onRenameFolder = { renaming = it })
                        }
                    }

                    SidebarFooter(
                        spaces = spaces,
                        current = pager.currentPage,
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
                                MenuAction("trash", "Clear Temporary Tabs", destructive = true) { vm.clearRegularTabs(space.uuid) }
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
    var dropLine by mutableStateOf<Float?>(null)
    var renameSpace by mutableStateOf<String?>(null)
    var revealedTab by mutableStateOf<String?>(null)
    var archiveRect = Rect.Zero
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
        dropLine = null
        overEssentials = false
    }
}

private fun tabMenu(vm: BrowserViewModel, tab: TabItem, context: android.content.Context): List<MenuAction> = listOfNotNull(
    MenuAction(if (tab.pinned) "unpin" else "pin", if (tab.pinned) "Unpin Tab" else "Pin Tab") { vm.setPinned(tab, !tab.pinned) },
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
                        if (gestures.move(gridOrigin + change.position, slop)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
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
            // The selected essential is outlined in its favicon's colour.
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

private const val FILLER_KEY = "__filler"

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

/** Where a dragged row would land: the edit to send, and the line to draw (root y). */
private fun dropTarget(space: Space, rows: List<Row>, gestures: SidebarGestures, held: Held, y: Float): Pair<LocalEdit.MoveItem?, Float?> {
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
    val line = rects.getOrNull(index)?.top ?: rects.lastOrNull { it != null }?.bottom
    val moved = ordered.toMutableList().apply { add(index.coerceAtMost(size), ItemRow(itemOf(held))) }
    if (moved.map { it.key } == rows.map { it.key }) return null to line
    val pinned = moved.take(moved.indexOf(DividerRow)).map { it.key }
    val regular = moved.subList(moved.indexOf(DividerRow) + 1, moved.indexOf(NewTabRow)).map { it.key }
    val hiddenPinned = space.pinnedItems.map { it.id }.filter { id -> rows.none { it.key == id } }
    return LocalEdit.placing(space.uuid, held.key, hiddenPinned + pinned, regular) to line
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
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val slop = with(LocalDensity.current) { 28.dp.toPx() }
    val collapsed = space.uuid in prefs.collapsedSpaces
    val rows = remember(space, collapsed) { rowsOf(space, collapsed) }
    val listState = rememberLazyListState()
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    val held = gestures.held
    // Temporary tabs sit at the bottom, within thumb reach; the gap above them absorbs the spare height.
    val filler by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.size < info.totalItemsCount) {
                0
            } else {
                val used = visible.filter { it.key != FILLER_KEY }.sumOf { it.size }
                (info.viewportEndOffset - info.viewportStartOffset - info.beforeContentPadding - info.afterContentPadding - used).coerceAtLeast(0)
            }
        }
    }

    @Composable
    fun LazyItemScope.SidebarRow(row: Row) {
        val lifted = held?.key == row.key
        val modifier = Modifier
            .animateItem()
            .onGloballyPositioned { gestures.rowRects[row.key] = it.boundsInRoot() }
            .graphicsLayer { alpha = if (lifted && gestures.dragging) 0.3f else 1f }
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
                    Text(
                        "↓ Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.clearRegularTabs(space.uuid) }
                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    )
                }
            }
            NewTabRow -> Row(
                modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).clickable {
                    onClose()
                    vm.openLauncher()
                }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(zenIcon("plus"), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                Text("New Tab", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                listOrigin = it.positionInRoot()
                // The pager keeps a neighbour page composed; only the visible one owns the drop area.
                if (isCurrent) gestures.listRect = it.boundsInRoot()
            }
            .pointerInput(space, collapsed, prefs.expandedFolders) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val long = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    val target = gestures.hit(listOrigin + long.position) ?: return@awaitEachGesture
                    if (target is HeldEssential) return@awaitEachGesture
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val menu = when (target) {
                        is HeldTab -> tabMenu(vm, target.tab, context)
                        is HeldFolder -> listOf(
                            MenuAction("folder", if (target.folder.id in prefs.expandedFolders) "Collapse Folder" else "Expand Folder") {
                                vm.settings.toggleFolder(target.folder.id)
                            },
                            MenuAction("edit", "Rename Folder") { onRenameFolder(target.folder) },
                        )
                        is HeldEssential -> emptyList()
                    }
                    gestures.begin(target, menu, listOrigin + long.position)
                    var edit: LocalEdit.MoveItem? = null
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        val point = listOrigin + change.position
                        if (gestures.move(point, slop)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        if (gestures.dragging) {
                            val rect = gestures.listRect
                            val edge = 64.dp.toPx()
                            if (point.y < rect.top + edge && point.y > rect.top) listState.dispatchRawDelta(-14f)
                            if (point.y > rect.bottom - edge) listState.dispatchRawDelta(14f)
                            if (rect.contains(point)) {
                                val (next, line) = dropTarget(space, rows, gestures, target, point.y)
                                edit = next
                                gestures.dropLine = line
                            } else {
                                edit = null
                                gestures.dropLine = null
                            }
                        }
                        if (!change.pressed) break
                    }
                    val toEssentials = gestures.dragging && gestures.overEssentials
                    val chosen = gestures.release()
                    when {
                        chosen != null -> chosen()
                        toEssentials && target is HeldTab -> vm.addEssential(target.tab)
                        edit != null -> vm.move(edit!!)
                    }
                }
            },
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp),
    ) {
        item(key = "__header") {
            SpaceHeader(space, collapsed, gestures, onToggle = { vm.settings.toggleSpaceCollapsed(space.uuid) }, onRename = { vm.renameSpace(space.uuid, it) })
        }
        val divider = rows.indexOf(DividerRow)
        items(rows.take(divider), key = { it.key }) { row -> SidebarRow(row) }
        item(key = FILLER_KEY) { Spacer(Modifier.height(with(LocalDensity.current) { filler.toDp() })) }
        items(rows.drop(divider), key = { it.key }) { row -> SidebarRow(row) }
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
                textStyle = MaterialTheme.typography.titleMedium.copy(color = onSurface, fontWeight = FontWeight.SemiBold),
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
                style = MaterialTheme.typography.titleMedium,
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
                .height(40.dp)
                .onGloballyPositioned { gestures.hitRects[folder.id] = it.boundsInRoot() to HeldFolder(folder, spaceUuid, it.boundsInRoot()) }
                .clip(RoundedCornerShape(10.dp))
                .clickable { vm.settings.toggleFolder(folder.id) }
                .padding(start = (10 + depth * 14).dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolderIcon(folder.icon, open = expanded, active = !expanded && containsActive, size = 22.dp)
            Text(
                folder.name,
                Modifier.weight(1f).padding(start = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(Icons.Default.KeyboardArrowRight, if (expanded) "Collapse" else "Expand", Modifier.size(18.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurface)
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
 * unpins a pinned tab; on a temporary tab it uncovers an archive button that
 * stays until tapped (archives the tab) or until a touch lands elsewhere.
 */
@Composable
private fun SwipeTab(vm: BrowserViewModel, tab: TabItem, spaceUuid: String, depth: Int, gestures: SidebarGestures, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reveal = with(density) { ArchiveButtonWidth.toPx() }
    val offset = remember(tab.id, tab.pinned) { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    val accent = MaterialTheme.colorScheme.primary
    val settle = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)

    LaunchedEffect(gestures.revealedTab) {
        if (gestures.revealedTab != tab.id && offset.targetValue != 0f) offset.animateTo(0f, settle)
    }

    Box(
        Modifier
            .onGloballyPositioned { gestures.hitRects[tab.id] = it.boundsInRoot() to HeldTab(tab, spaceUuid, it.boundsInRoot()) }
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { dx ->
                    scope.launch { offset.snapTo((offset.value + dx).coerceIn(-width.toFloat(), width.toFloat())) }
                },
                onDragStopped = { velocity ->
                    val threshold = width * 0.35f
                    val value = offset.value
                    when {
                        value > threshold || (value > 0f && velocity > 1500f) -> {
                            vm.setPinned(tab, !tab.pinned)
                            offset.animateTo(0f, settle)
                        }
                        value < 0f && tab.pinned && (value < -threshold || velocity < -1500f) -> {
                            vm.setPinned(tab, false)
                            offset.animateTo(0f, settle)
                        }
                        value < -reveal / 2 && !tab.pinned -> {
                            gestures.revealedTab = tab.id
                            offset.animateTo(-reveal, settle)
                        }
                        else -> {
                            if (gestures.revealedTab == tab.id) gestures.revealedTab = null
                            offset.animateTo(0f, settle)
                        }
                    }
                },
            ),
    ) {
        val value = offset.value
        if (value != 0f) {
            val toEnd = value < 0f
            val archives = toEnd && !tab.pinned
            val icon = when {
                archives -> "archive"
                tab.pinned -> "unpin"
                else -> "pin"
            }
            val progress = (kotlin.math.abs(value) / (width * 0.35f)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background((if (archives) DestructiveRed else accent).copy(alpha = 0.35f + 0.55f * progress)),
                contentAlignment = if (toEnd) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(ArchiveButtonWidth)
                        .then(
                            if (archives) {
                                Modifier
                                    .onGloballyPositioned { gestures.archiveRect = it.boundsInRoot() }
                                    .clickable {
                                        gestures.revealedTab = null
                                        vm.closeTab(tab.id)
                                    }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(zenIcon(icon), if (archives) "Archive tab" else null, Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
        Box(Modifier.offset { IntOffset(value.roundToInt(), 0) }) {
            TabRow(tab, depth, tab.id == vm.activeTab, opaque = value != 0f) {
                vm.selectTab(tab)
                onOpen()
            }
        }
    }
}

private val ArchiveButtonWidth = 72.dp

@Composable
private fun TabRow(tab: TabItem, depth: Int, selected: Boolean, opaque: Boolean, onClick: () -> Unit) {
    val backdrop = LocalRowBackdrop.current
    val selectedBg = selectedTabBackground()
    // While sliding, the row is solid so the action colour never shows through it.
    val background = when {
        opaque && selected -> selectedBg.over(backdrop)
        opaque -> backdrop
        selected -> selectedBg
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = (10 + depth * 14).dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Favicon(tab.url, tab.icon, 18.dp)
        Text(
            tab.title,
            Modifier.padding(start = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
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

    val line = gestures.dropLine
    if (line != null && gestures.dragging && !gestures.overEssentials) {
        Box(
            Modifier
                .offset { IntOffset((gestures.listRect.left - origin.x + 12.dp.toPx()).roundToInt(), (line - origin.y - 1.dp.toPx()).roundToInt()) }
                .width(with(density) { gestures.listRect.width.toDp() } - 24.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }

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
            Favicon(held.tab.url, held.tab.icon, 18.dp)
            Text(held.tab.title, Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = onSurface, style = MaterialTheme.typography.bodyMedium)
        }
        is HeldFolder -> Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            FolderIcon(held.folder.icon, open = false, active = false, size = 22.dp)
            Text(held.folder.name, Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = onSurface, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SidebarFooter(
    spaces: List<Space>,
    current: Int,
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
        IconButton(onClick = onLibrary) { Icon(zenIcon("library"), "Library", Modifier.size(18.dp), tint = tint) }
        LazyRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(spaces.size) { i -> SpaceDot(spaces[i], i == current, Modifier.holdMenu(spaceMenu) { menuFor(i) }) { onPick(i) } }
        }
        // Sync runs on its own, like desktop; only a failure shows up here.
        if (status is SyncStatus.Failed) {
            IconButton(onClick = onRetry) { Icon(Icons.Default.Warning, "Sync failed: ${status.message}", Modifier.size(18.dp), tint = Color(0xFFFFB347)) }
        }
        IconButton(onClick = onPrivate) { Icon(zenIcon("privateBrowsing"), "Private tabs", Modifier.size(18.dp), tint = tint) }
    }
}

@Composable
private fun SpaceDot(space: Space, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val tint = if (selected) onSurface else onSurface.copy(alpha = onSurface.alpha * 0.5f)
    Box(
        modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) toolbarElementBackground() else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ZenIconView(space.icon, 16.dp, tint) {
            Box(Modifier.size(if (selected) 8.dp else 6.dp).clip(RoundedCornerShape(50)).background(tint))
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
