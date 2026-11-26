package app.zen.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zen.android.browser.Updates
import app.zen.android.sync.SyncStatus

/**
 * First launch: the three things that make Zen work well on this phone,
 * each a tap away and each skippable. Shown once; Settings keeps them all.
 */
@Composable
fun WelcomeScreen(vm: BrowserViewModel, dark: Boolean) {
    BackHandler { vm.finishWelcome() }
    val context = LocalContext.current
    val status by vm.status.collectAsStateWithLifecycle()
    val browser = rememberDefaultBrowser()
    val spec = remember(dark) { ZenGradient.spec(null, dark) }
    val chrome = remember(dark) { ZenGradient.chrome(null, dark) }
    val text = Color(chrome.text)
    val signedIn = status != SyncStatus.SignedOut

    Box(Modifier.fillMaxSize().zenSpaceBackground(spec).clickable(enabled = false) {}) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            ZenLogo(Modifier.size(64.dp), color = text)
            Spacer(Modifier.height(20.dp))
            Text("Welcome to Zen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = text)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your desktop spaces, pinned tabs and folders, in your pocket. Ads are already blocked.",
                style = MaterialTheme.typography.bodyLarge,
                color = text.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WelcomeStep(
                    icon = "sidebar",
                    title = "Sync with Zen desktop",
                    detail = "Sign in with your Mozilla account to bring your spaces over.",
                    done = signedIn,
                    doneLabel = vm.accountEmail ?: "Signed in",
                    action = "Sign in",
                    text = text,
                ) { vm.beginLogin() }
                WelcomeStep(
                    icon = "link",
                    title = "Open links in Zen",
                    detail = "Make Zen the browser for links you tap in other apps.",
                    done = browser.isDefault,
                    doneLabel = "Zen is your default browser",
                    action = "Set as default",
                    text = text,
                ) { browser.ask() }
                WelcomeStep(
                    icon = "downloads",
                    title = "Stay up to date",
                    detail = "Zen is not on the Play Store. Obtainium installs each new version, including security fixes.",
                    done = false,
                    doneLabel = "",
                    action = if (Updates.obtainiumInstalled(context)) "Add to Obtainium" else "Get Obtainium",
                    text = text,
                ) {
                    if (!openUri(context, Updates.OBTAINIUM_ADD)) openUri(context, Updates.OBTAINIUM_DOWNLOAD)
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))
            Text(
                "Start browsing",
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(text)
                    .clickable { vm.finishWelcome() }
                    .padding(vertical = 16.dp),
                color = if (text.isLight()) Color.Black else Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun WelcomeStep(
    icon: String,
    title: String,
    detail: String,
    done: Boolean,
    doneLabel: String,
    action: String,
    text: Color,
    onAction: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(text.copy(alpha = 0.08f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(text.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(zenIcon(if (done) "security" else icon), null, Modifier.size(18.dp), tint = if (done) SecureGreen else text)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = text)
            Text(if (done) doneLabel else detail, style = MaterialTheme.typography.bodySmall, color = text.copy(alpha = 0.7f))
        }
        if (!done) {
            Text(
                action,
                Modifier.clip(RoundedCornerShape(12.dp)).background(text.copy(alpha = 0.12f)).clickable(onClick = onAction).padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = text,
            )
        }
    }
}

private fun openUri(context: android.content.Context, uri: String): Boolean = try {
    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: android.content.ActivityNotFoundException) {
    false
}
