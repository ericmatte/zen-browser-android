package app.zen.android.ui

import androidx.compose.runtime.remember

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zen.android.sync.TabItem

@Composable
fun PrivateSidebar(vm: BrowserViewModel, onTab: (TabItem) -> Unit, onClose: () -> Unit, onLeave: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PrivateTint, PrivateBackdrop)))
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
                    onClose()
                    vm.closePrivateTabs()
                }.padding(10.dp),
            )
        }
        Text(
            "Private tabs never sync and are forgotten when you close them.",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )
        // Tabs gather at the bottom, within thumb reach, like the spaces sidebar.
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.BottomCenter) {
            val reveal = remember { RevealGroup() }
            Column(Modifier.verticalScroll(rememberScrollState(), reverseScrolling = true)) {
                vm.privateTabs.forEach { tab ->
                    key(tab.id) {
                        PrivateTabRow(tab, tab.id == vm.activeTab, reveal, onClick = { onTab(tab) }, onCloseTab = { vm.closeTab(tab.id) })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(SidebarRowHeight).clip(RoundedCornerShape(10.dp)).clickable {
                        onClose()
                        vm.openLauncher()
                    }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(SidebarIconSize), contentAlignment = Alignment.Center) {
                        Icon(zenIcon("plus"), null, Modifier.size(SidebarIconSize - 2.dp), tint = Color.White.copy(alpha = 0.7f))
                    }
                    Text("New private tab", Modifier.padding(start = SidebarIconGap), style = sidebarText(), color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Back to spaces",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onLeave).padding(12.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.showSettings = true; onClose() }) { Icon(zenIcon("settings"), "Settings", Modifier.size(18.dp), tint = Color.White) }
        }
    }
}

/** Swiping left closes a private tab at once, with the same red trail and trash icon as the spaces sidebar. */
@Composable
private fun PrivateTabRow(tab: TabItem, selected: Boolean, group: RevealGroup, onClick: () -> Unit, onCloseTab: () -> Unit) {
    SwipeRow(
        key = tab.id,
        group = group,
        reveal = null,
        commitStart = null,
        commitEnd = SwipeAction("trash", "Close tab", DestructiveRed, onCloseTab),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(SidebarRowHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Favicon(tab.url, null, SidebarIconSize)
            Text(tab.title, Modifier.padding(start = SidebarIconGap), style = sidebarText(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
