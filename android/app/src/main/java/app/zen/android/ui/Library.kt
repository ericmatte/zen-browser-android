package app.zen.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zen.android.browser.HistorySort
import app.zen.android.browser.UrlInput
import app.zen.android.browser.Visit
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** library-filter-*: the "When was it visited?" group. */
enum class HistoryWhen(val label: String, val days: Int?) { ANY("Any time", null), TODAY("Today", 0), WEEK("This Week", 7), MONTH("This Month", 30) }

/** library-history-clear-*: the ranges desktop offers when clearing history. */
private enum class ClearRange(val label: String, val hours: Long?) {
    HOUR("Last hour", 1), HALF_DAY("Last 12 hours", 12), TODAY("Today", null), WEEK("Last week", 24 * 7), MONTH("Last month", 24 * 30), ALL("Everything", null)
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayLabel(time: Long, today: Long): String = when {
    time >= today -> "Today"
    time >= today - 86_400_000 -> "Yesterday"
    else -> DateFormat.getDateInstance(DateFormat.FULL).format(Date(time))
}

/** Port of the library's History section (src/zen/library/sections/ZenLibraryHistorySection.mjs). */
@Composable
fun LibraryScreen(vm: BrowserViewModel, dark: Boolean, onOpened: () -> Unit) {
    BackHandler { vm.showLibrary = false }
    val theme = vm.space(vm.activeSpace)?.theme
    val spec = remember(theme, dark) { ZenGradient.spec(theme, dark) }
    val chrome = remember(theme, dark) { ZenGradient.chrome(theme, dark) }
    val text = Color(chrome.text)
    val colors = MaterialTheme.colorScheme.copy(onSurface = text, onSurfaceVariant = text.copy(alpha = text.alpha * 0.65f), primary = Color(chrome.accent))

    var query by remember { mutableStateOf("") }
    var whenFilter by remember { mutableStateOf(HistoryWhen.ANY) }
    var sort by remember { mutableStateOf(HistorySort.DATE) }
    var revision by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val visitMenu = remember { HoldMenuState() }
    val today = startOfToday()
    val entries = remember(query, whenFilter, sort, revision) {
        val since = whenFilter.days?.let { if (it == 0) today else System.currentTimeMillis() - it * 86_400_000L }
        vm.historyEntries(query, since, sort)
    }

    Box(Modifier.fillMaxSize().zenSpaceBackground(spec)) {
        MaterialTheme(colorScheme = colors) {
            CompositionLocalProvider(LocalLightChrome provides chrome.lightText) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.showLibrary = false }) { Icon(zenIcon("back"), "Back", Modifier.size(18.dp), tint = text) }
                        Text("History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = text)
                    }
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp)).background(toolbarElementBackground()).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(zenIcon("search-glass"), null, Modifier.size(16.dp), tint = text.copy(alpha = 0.7f))
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = text),
                                cursorBrush = SolidColor(text),
                                modifier = Modifier.weight(1f).padding(start = 10.dp),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (query.isEmpty()) Text("Search History…", color = text.copy(alpha = 0.5f))
                                        inner()
                                    }
                                },
                            )
                        }
                        FilterButton(whenFilter, sort, { whenFilter = it }, { sort = it }) { range ->
                            val since = when (range) {
                                ClearRange.ALL -> null
                                ClearRange.TODAY -> today
                                else -> System.currentTimeMillis() - range.hours!! * 3_600_000
                            }
                            vm.clearHistorySince(since)
                            revision++
                        }
                    }
                    if (entries.isEmpty()) {
                        Text("No history found", Modifier.padding(24.dp), color = text.copy(alpha = 0.6f))
                    }
                    LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                        val grouped: Map<String, List<Visit>> = when (sort) {
                            HistorySort.DATE -> entries.groupBy { dayLabel(it.lastVisit, today) }
                            HistorySort.SITE -> entries.groupBy { UrlInput.display(it.url) }
                            else -> mapOf("" to entries)
                        }
                        grouped.forEach { (header, visits) ->
                            if (header.isNotEmpty()) {
                                item(key = "h:$header") {
                                    Text(
                                        header,
                                        Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = text.copy(alpha = 0.65f),
                                    )
                                }
                            }
                            items(visits, key = { "v:${header}:${it.url}" }) { visit ->
                                fun open() {
                                    vm.showLibrary = false
                                    vm.newTab(visit.url)
                                    onOpened()
                                }
                                VisitRow(
                                    visit,
                                    Modifier.holdMenu(visitMenu) {
                                        listOf(
                                            MenuAction("open", "Open") { open() },
                                            MenuAction("edit-copy", "Copy") {
                                                copyToClipboard(context, visit.url)
                                                vm.message = "Link copied"
                                            },
                                            MenuAction("trash", "Forget This Page", destructive = true) {
                                                vm.forgetVisit(visit.url)
                                                revision++
                                            },
                                        )
                                    },
                                    onOpen = ::open,
                                )
                            }
                        }
                    }
                }
                HoldMenuHost(visitMenu)
            }
        }
    }
}

@Composable
private fun FilterButton(
    whenFilter: HistoryWhen,
    sort: HistorySort,
    onWhen: (HistoryWhen) -> Unit,
    onSort: (HistorySort) -> Unit,
    onClear: (ClearRange) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val text = MaterialTheme.colorScheme.onSurface
    Box(Modifier.padding(start = 8.dp)) {
        Row(
            Modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).background(toolbarElementBackground())
                .combinedClickable(onClick = { open = true }).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(zenIcon("circle-bars-filter"), null, Modifier.size(16.dp), tint = text)
            Text("Filter", Modifier.padding(start = 8.dp), color = text, fontWeight = FontWeight.Medium)
        }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(14.dp)) {
            Text("When was it visited?", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            HistoryWhen.entries.forEach { w ->
                DropdownMenuItem(text = { Text(w.label, fontWeight = if (w == whenFilter) FontWeight.SemiBold else FontWeight.Normal) }, onClick = { onWhen(w) })
            }
            HorizontalDivider()
            Text("Sort by", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            HistorySort.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.label, fontWeight = if (s == sort) FontWeight.SemiBold else FontWeight.Normal) }, onClick = { onSort(s) })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Clear history…", color = MaterialTheme.colorScheme.error) }, onClick = {
                open = false
                clearing = true
            })
        }
        DropdownMenu(clearing, { clearing = false }, shape = RoundedCornerShape(14.dp)) {
            ClearRange.entries.forEach { range ->
                DropdownMenuItem(text = { Text(range.label) }, onClick = {
                    clearing = false
                    onClear(range)
                })
            }
        }
    }
}

@Composable
private fun VisitRow(visit: Visit, modifier: Modifier, onOpen: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Favicon(visit.url, null, 22.dp)
        Column(Modifier.padding(start = 16.dp)) {
            Text(visit.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(
                visit.url.removePrefix("https://").removePrefix("http://").removePrefix("www."),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
