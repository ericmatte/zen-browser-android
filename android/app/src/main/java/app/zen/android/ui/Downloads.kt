package app.zen.android.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zen.android.browser.DownloadItem
import app.zen.android.browser.DownloadStatus
import app.zen.android.browser.Downloads
import java.text.DateFormat
import java.util.Date

/** The library's Downloads section: tap opens the file, hold for share, delete or remove from the list. */
@Composable
fun DownloadsSection(vm: BrowserViewModel, menu: HoldMenuState, modifier: Modifier) {
    val context = LocalContext.current
    val list by vm.downloads.collectAsStateWithLifecycle()
    val onSurface = MaterialTheme.colorScheme.onSurface
    if (list.isEmpty()) {
        Text("No downloads yet", Modifier.padding(24.dp), color = onSurface.copy(alpha = 0.6f))
        return
    }
    LazyColumn(modifier.padding(top = 8.dp)) {
        items(list, key = { it.id }) { item ->
            fun open() {
                if (!openDownload(context, item)) vm.message = "${item.name} is no longer on this phone"
            }
            DownloadRow(
                item,
                Modifier.holdMenu(menu) {
                    listOfNotNull(
                        MenuAction("open", "Open") { open() }.takeIf { item.status == DownloadStatus.DONE },
                        MenuAction("share", "Share") { shareDownload(context, item) }.takeIf { item.status == DownloadStatus.DONE },
                        MenuAction("link", "Copy Download Link") {
                            copyToClipboard(context, item.source)
                            vm.message = "Link copied"
                        }.takeIf { item.source.startsWith("http") },
                        MenuAction("close", "Remove From List") { vm.removeDownload(item, deleteFile = false) },
                        MenuAction("trash", "Delete File", destructive = true) { vm.removeDownload(item, deleteFile = true) }.takeIf { item.uri != null },
                    )
                },
                onOpen = { if (item.status == DownloadStatus.DONE) open() },
            )
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, modifier: Modifier, onOpen: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ListRow(modifier, onOpen, leading = {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Icon(zenIcon("downloads"), null, Modifier.size(20.dp), tint = if (item.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else onSurface)
        }
    }) {
        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = onSurface, fontWeight = FontWeight.Medium)
        when (item.status) {
            DownloadStatus.RUNNING -> {
                val progress = item.progress
                if (progress != null) {
                    LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)))
                }
                Text(
                    Downloads.size(item.received) + (if (item.total > 0) " of " + Downloads.size(item.total) else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            DownloadStatus.DONE -> Text(
                Downloads.size(item.total) + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.time)),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            DownloadStatus.FAILED -> Text("Failed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Hands the file to whichever app the user picks for its type. */
fun openDownload(context: Context, item: DownloadItem): Boolean {
    val uri = item.uri ?: return false
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(uri), item.mime ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(view)
        true
    } catch (_: ActivityNotFoundException) {
        // Android's own picker then says that no app handles this kind of file.
        context.startActivity(Intent.createChooser(view, item.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }
}

private fun shareDownload(context: Context, item: DownloadItem) {
    val uri = item.uri ?: return
    val send = Intent(Intent.ACTION_SEND)
        .setType(item.mime ?: "*/*")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, item.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
