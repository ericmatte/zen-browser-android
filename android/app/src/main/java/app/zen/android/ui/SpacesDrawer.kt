package app.zen.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zen.android.browser.UrlInput
import app.zen.android.sync.FolderItem
import app.zen.android.sync.SidebarItem
import app.zen.android.sync.Space
import app.zen.android.sync.SpacesState
import app.zen.android.sync.SyncStatus
import app.zen.android.sync.TabItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpacesDrawer(
    state: SpacesState,
    status: SyncStatus,
    activeSpace: String?,
    activeTab: String,
    onSpaceChange: (String) -> Unit,
    onTab: (TabItem) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSync: () -> Unit,
) {
    val spaces = state.spaces()
    val essentials = state.essentials()

    ModalDrawerSheet(drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (essentials.isNotEmpty()) {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(essentials, key = { it.id }) { tab -> EssentialChip(tab, tab.id == activeTab) { onTab(tab) } }
                }
            }

            if (spaces.isEmpty()) {
                EmptySpaces(status, onSignIn, Modifier.weight(1f))
            } else {
                val initial = spaces.indexOfFirst { it.uuid == activeSpace }.coerceAtLeast(0)
                val pager = rememberPagerState(initialPage = initial) { spaces.size }
                LaunchedEffect(pager.currentPage, spaces.size) {
                    spaces.getOrNull(pager.currentPage)?.let { onSpaceChange(it.uuid) }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f), key = { spaces[it].uuid }) { page ->
                    SpacePage(spaces[page], activeTab, onTab, onCloseTab, onNewTab)
                }
                SpaceDots(spaces, pager.currentPage)
            }

            SyncFooter(status, onSignIn, onSignOut, onSync)
        }
    }
}

@Composable
private fun SpacePage(
    space: Space,
    activeTab: String,
    onTab: (TabItem) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
        item {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                space.icon?.takeIf { !it.contains(':') }?.let { Text(it, Modifier.padding(end = 8.dp)) }
                Text(space.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        val pinned = space.items.filter { it !is TabItem || it.pinned }
        val regular = space.items.filter { it is TabItem && !it.pinned }
        items(pinned, key = { it.id }) { SidebarRow(it, 0, activeTab, onTab, onCloseTab) }
        if (pinned.isNotEmpty()) {
            item { Spacer(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).size(1.dp).background(MaterialTheme.colorScheme.outlineVariant)) }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onNewTab).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Text("New tab", Modifier.padding(start = 10.dp))
            }
        }
        items(regular, key = { it.id }) { SidebarRow(it, 0, activeTab, onTab, onCloseTab) }
    }
}

@Composable
private fun SidebarRow(
    item: SidebarItem,
    depth: Int,
    activeTab: String,
    onTab: (TabItem) -> Unit,
    onCloseTab: (String) -> Unit,
) {
    when (item) {
        is FolderItem -> Column {
            Text(
                "▸ ${item.name}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = (12 + depth * 14).dp, top = 10.dp, bottom = 4.dp),
            )
            item.items.forEach { SidebarRow(it, depth + 1, activeTab, onTab, onCloseTab) }
        }
        is TabItem -> {
            val selected = item.id == activeTab
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                    .clickable { onTab(item) }
                    .padding(start = (12 + depth * 14).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        UrlInput.display(item.url),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onCloseTab(item.id) }) { Icon(Icons.Default.Close, "Close tab", Modifier.size(16.dp)) }
            }
        }
    }
}

@Composable
private fun EssentialChip(tab: TabItem, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(UrlInput.display(tab.url).take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SpaceDots(spaces: List<Space>, current: Int) {
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
        spaces.forEachIndexed { i, space ->
            val label = space.icon?.takeIf { !it.contains(':') }
            if (label != null) {
                Text(label, Modifier.padding(horizontal = 4.dp), color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Box(
                    Modifier.padding(4.dp).size(if (i == current) 8.dp else 6.dp).clip(CircleShape)
                        .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

@Composable
private fun EmptySpaces(status: SyncStatus, onSignIn: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (status == SyncStatus.SignedOut) {
            Text("Sign in with your Mozilla account to see your Zen spaces.")
            TextButton(onClick = onSignIn) { Text("Sign in") }
        } else {
            Text("No spaces yet. Turn on “Sync your sidebar across devices” in Zen desktop.")
        }
    }
}

@Composable
private fun SyncFooter(status: SyncStatus, onSignIn: () -> Unit, onSignOut: () -> Unit, onSync: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val label = when (status) {
            SyncStatus.SignedOut -> "Not syncing"
            SyncStatus.Idle -> "Synced"
            SyncStatus.Syncing -> "Syncing…"
            is SyncStatus.Failed -> "Sync error: ${status.message}"
        }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (status == SyncStatus.SignedOut) {
            TextButton(onClick = onSignIn) { Text("Sign in") }
        } else {
            IconButton(onClick = onSync) { Icon(Icons.Default.Refresh, "Sync now") }
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick))
