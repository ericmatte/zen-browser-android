package app.zen.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

class SidebarActions(
    val onSpaceChange: (String) -> Unit,
    val onTab: (TabItem) -> Unit,
    val onCloseTab: (String) -> Unit,
    val onClearTabs: (String) -> Unit,
    val onMove: (LocalEdit.MoveItem) -> Unit,
    val onToggleFolder: (String) -> Unit,
    val onNewTab: () -> Unit,
    val onSignIn: () -> Unit,
    val onSync: () -> Unit,
    val onSettings: () -> Unit,
)

@Composable
fun SpacesSidebar(
    state: SpacesState,
    status: SyncStatus,
    expandedFolders: Set<String>,
    activeSpace: String?,
    activeTab: String,
    dark: Boolean,
    actions: SidebarActions,
    onPrivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spaces = state.spaces()
    val essentials = state.essentials()
    val initial = spaces.indexOfFirst { it.uuid == activeSpace }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initial) { spaces.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager.settledPage, spaces.size) {
        spaces.getOrNull(pager.settledPage)?.let { actions.onSpaceChange(it.uuid) }
    }
    LaunchedEffect(activeSpace) {
        val index = spaces.indexOfFirst { it.uuid == activeSpace }
        if (index >= 0 && index != pager.currentPage) pager.animateScrollToPage(index)
    }

    val theme = spaces.getOrNull(pager.settledPage)?.theme
    Box(modifier.fillMaxSize()) {
        androidx.compose.animation.Crossfade(theme, Modifier.matchParentSize(), animationSpec = tween(350), label = "spaceTheme") { t ->
            Box(Modifier.fillMaxSize().zenSpaceBackground(remember(t, dark) { ZenGradient.spec(t, dark) }))
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            if (essentials.isNotEmpty()) EssentialsGrid(essentials, activeTab, actions.onTab)

            if (spaces.isEmpty()) {
                EmptySpaces(status, actions.onSignIn, Modifier.weight(1f))
            } else {
                HorizontalPager(state = pager, modifier = Modifier.weight(1f), key = { spaces[it].uuid }, beyondViewportPageCount = 1) { page ->
                    SpacePage(spaces[page], activeTab, expandedFolders, actions)
                }
            }

            SidebarFooter(
                spaces = spaces,
                current = pager.currentPage,
                status = status,
                onPick = { scope.launch { pager.animateScrollToPage(it) } },
                onPrivate = onPrivate,
                actions = actions,
            )
        }
    }
}

/** vertical-tabs.css: --tab-background-color-selected. */
@Composable
fun selectedTabBackground(): Color =
    if (MaterialTheme.colorScheme.surface.isLight()) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.2f)

/** zen-theme.css: --zen-toolbar-element-bg. */
@Composable
fun toolbarElementBackground(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = if (MaterialTheme.colorScheme.surface.isLight()) 0.08f else 0.15f)

@Composable
private fun EssentialsGrid(essentials: List<TabItem>, activeTab: String, onTab: (TabItem) -> Unit) {
    Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        essentials.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { tab ->
                    val selected = tab.id == activeTab
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) selectedTabBackground() else toolbarElementBackground())
                            .clickable { onTab(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        // zen.theme.essentials-favicon-bg: the selected essential glows with its own blurred favicon.
                        if (selected) {
                            Favicon(tab.url, tab.icon, 64.dp, Modifier.blur(20.dp).graphicsLayer { alpha = 0.55f })
                        }
                        Favicon(tab.url, tab.icon, 20.dp)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
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

private data object NewTabRow : Row {
    override val key = "__newtab"
}

private fun rowsOf(space: Space): List<Row> =
    space.pinnedItems.map(::ItemRow) + DividerRow + NewTabRow + space.regularTabs.map(::ItemRow)

/**
 * Long-press drag reordering over a LazyColumn. Rows above [DividerRow] are
 * pinned, rows below [NewTabRow] are regular tabs, like the desktop sidebar.
 */
private class ReorderState(val list: LazyListState, val rows: MutableList<Row>) {
    var dragging by mutableStateOf<String?>(null)
    var offset by mutableFloatStateOf(0f)

    fun start(key: String) {
        dragging = key
        offset = 0f
    }

    fun drag(dy: Float): Boolean {
        val key = dragging ?: return false
        offset += dy
        val layout = list.layoutInfo
        val visible = layout.visibleItemsInfo
        val current = visible.firstOrNull { it.key == key } ?: return false
        val middle = current.offset + offset + current.size / 2f

        val edge = 72f
        val scroll = when {
            middle < layout.viewportStartOffset + edge -> -18f
            middle > layout.viewportEndOffset - edge -> 18f
            else -> 0f
        }
        if (scroll != 0f) offset += list.dispatchRawDelta(scroll)

        val target = visible.firstOrNull { it.key != key && middle >= it.offset && middle < it.offset + it.size } ?: return false
        val from = rows.indexOfFirst { it.key == key }
        val targetIndex = rows.indexOfFirst { it.key == target.key }
        if (from < 0 || targetIndex < 0) return false
        val divider = rows.indexOf(DividerRow)
        val newTab = rows.indexOf(NewTabRow)
        val to = when (target.key) {
            DividerRow.key, NewTabRow.key -> if (from < targetIndex) newTab else divider
            else -> targetIndex
        }
        val moved = rows[from] as ItemRow
        if (moved.item is FolderItem && to > divider) return false
        val anchor = visible.firstOrNull { it.key == rows[to].key } ?: return false
        val newTop = if (to > from) anchor.offset + anchor.size - current.size else anchor.offset
        rows.add(to, rows.removeAt(from))
        offset -= newTop - current.offset
        return true
    }

    fun end(space: Space, onMove: (LocalEdit.MoveItem) -> Unit) {
        val key = dragging ?: return
        dragging = null
        offset = 0f
        val divider = rows.indexOf(DividerRow)
        val ids = rows.filterIsInstance<ItemRow>().map { it.key }
        val pinned = rows.take(divider).map { it.key }
        val regular = ids - pinned.toSet()
        val original = rowsOf(space)
        val originalPinned = original.take(original.indexOf(DividerRow)).map { it.key }
        if (pinned == originalPinned && regular == space.regularTabs.map { it.id }) return
        onMove(LocalEdit.placing(space.uuid, key, pinned, regular))
    }
}

@Composable
private fun SpacePage(space: Space, activeTab: String, expandedFolders: Set<String>, actions: SidebarActions) {
    val rows = remember(space) { mutableStateListOf<Row>().apply { addAll(rowsOf(space)) } }
    val listState = rememberLazyListState()
    val reorder = remember(space) { ReorderState(listState, rows) }
    val haptics = LocalHapticFeedback.current
    val hasRegular = space.regularTabs.isNotEmpty()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp),
    ) {
        item(key = "__header") { SpaceHeader(space) }
        itemsIndexed(rows, key = { _, row -> row.key }) { _, row ->
            val dragged = reorder.dragging == row.key
            val rowModifier = Modifier
                .zIndex(if (dragged) 1f else 0f)
                .graphicsLayer {
                    translationY = if (dragged) reorder.offset else 0f
                    shadowElevation = if (dragged) 12f else 0f
                    scaleX = if (dragged) 1.02f else 1f
                    scaleY = if (dragged) 1.02f else 1f
                }
                .then(if (dragged) Modifier else Modifier.animateItem())
            when (row) {
                is ItemRow -> Box(
                    rowModifier.pointerInput(row.key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                reorder.start(row.key)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                if (reorder.drag(amount.y)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            },
                            onDragEnd = { reorder.end(space, actions.onMove) },
                            onDragCancel = { reorder.end(space, actions.onMove) },
                        )
                    },
                ) {
                    SidebarEntry(row.item, 0, activeTab, expandedFolders, actions)
                }
                DividerRow -> Row(rowModifier.fillMaxWidth().height(28.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)))
                    if (hasRegular) {
                        Text(
                            "↓ Clear",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { actions.onClearTabs(space.uuid) }.padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        )
                    }
                }
                NewTabRow -> Row(
                    rowModifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = actions.onNewTab).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(zenIcon("plus"), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("New Tab", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SpaceHeader(space: Space) {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ZenIconView(space.icon, 18.dp, MaterialTheme.colorScheme.onSurface)
        Text(
            space.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (space.icon != null) 10.dp else 0.dp),
        )
    }
}

@Composable
private fun SidebarEntry(item: SidebarItem, depth: Int, activeTab: String, expandedFolders: Set<String>, actions: SidebarActions) {
    when (item) {
        is TabItem -> SwipeableTab(item, depth, item.id == activeTab, actions)
        is FolderItem -> FolderEntry(item, depth, activeTab, expandedFolders, actions)
    }
}

@Composable
private fun FolderEntry(folder: FolderItem, depth: Int, activeTab: String, expandedFolders: Set<String>, actions: SidebarActions) {
    val expanded = folder.id in expandedFolders
    val chevron by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { actions.onToggleFolder(folder.id) }
                .padding(start = (10 + depth * 14).dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolderIcon(folder.icon, open = expanded, active = !expanded && folder.items.any { c -> c.flattenTabs().any { it.id == activeTab } }, size = 22.dp)
            Text(
                folder.name,
                Modifier.weight(1f).padding(start = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                Icons.Default.KeyboardArrowRight,
                if (expanded) "Collapse" else "Expand",
                Modifier.size(18.dp).rotate(chevron),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Like desktop, a collapsed folder still shows its active tab.
        val visibleWhenCollapsed = folder.items.filter { child -> child.flattenTabs().any { it.id == activeTab } }
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column { folder.items.forEach { SidebarEntry(it, depth + 1, activeTab, expandedFolders, actions) } }
        }
        if (!expanded) visibleWhenCollapsed.forEach { SidebarEntry(it, depth + 1, activeTab, expandedFolders, actions) }
    }
}

@Composable
private fun SwipeableTab(tab: TabItem, depth: Int, selected: Boolean, actions: SidebarActions) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) actions.onCloseTab(tab.id) },
        backgroundContent = {
            if (state.dismissDirection != SwipeToDismissBoxValue.EndToStart) return@SwipeToDismissBox
            val reveal = state.progress
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f + 0.5f * reveal)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(zenIcon("trash"), "Close tab", Modifier.padding(end = 16.dp).size(16.dp), tint = Color.White)
            }
        },
    ) {
        TabRow(tab, depth, selected) { actions.onTab(tab) }
    }
}

@Composable
private fun TabRow(tab: TabItem, depth: Int, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) selectedTabBackground() else Color.Transparent
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
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun SidebarFooter(spaces: List<Space>, current: Int, status: SyncStatus, onPick: (Int) -> Unit, onPrivate: () -> Unit, actions: SidebarActions) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onSettings) { Icon(zenIcon("settings"), "Settings", Modifier.size(18.dp), tint = muted) }
        LazyRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(spaces.size) { i -> SpaceDot(spaces[i], i == current) { onPick(i) } }
        }
        IconButton(onClick = onPrivate) { Icon(zenIcon("privateBrowsing"), "Private tabs", Modifier.size(18.dp), tint = muted) }
        SyncButton(status, actions)
    }
}

@Composable
private fun SpaceDot(space: Space, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ZenIconView(space.icon, 16.dp, tint) {
            Box(Modifier.size(if (selected) 8.dp else 6.dp).clip(RoundedCornerShape(50)).background(tint))
        }
    }
}

@Composable
private fun SyncButton(status: SyncStatus, actions: SidebarActions) {
    when (status) {
        SyncStatus.SignedOut -> IconButton(onClick = actions.onSignIn) {
            Icon(zenIcon("reload"), "Sign in to sync", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        is SyncStatus.Failed -> IconButton(onClick = actions.onSync) {
            Icon(Icons.Default.Warning, "Sync failed: ${status.message}", tint = MaterialTheme.colorScheme.error)
        }
        else -> {
            val spinning = status == SyncStatus.Syncing
            val transition = rememberInfiniteTransition(label = "sync")
            val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spin")
            IconButton(onClick = actions.onSync) {
                Icon(
                    zenIcon("reload"),
                    if (spinning) "Syncing" else "Sync now",
                    Modifier.size(18.dp).rotate(if (spinning) angle else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            Text("Your spaces live here", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Sign in with your Mozilla account to bring your Zen spaces, pinned tabs and folders from desktop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = onSignIn) { Text("Sign in") }
        } else {
            Text(
                "No spaces yet. Turn on “Sync your sidebar across devices” in Zen desktop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
