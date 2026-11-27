package app.zen.android.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.zen.android.browser.Release
import app.zen.android.browser.Updates

/** Dark enough to read as text on light surfaces, bright enough on dark ones. */
val UpdateOrange = Color(0xFFF08A24)

/** Marks the ⋯ button while an app update is waiting. */
@Composable
fun UpdateDot(modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(UpdateOrange))
}

/** Obtainium does the installing; without it, the release page offers the APK. */
fun installUpdate(context: Context, vm: BrowserViewModel, update: Release) {
    if (Updates.openObtainium(context)) return
    vm.showSettings = false
    vm.newTab(update.pageUrl)
}
