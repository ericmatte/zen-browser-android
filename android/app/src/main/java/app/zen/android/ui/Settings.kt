package app.zen.android.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.content.Context
import app.zen.android.browser.Updates
import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zen.android.BuildConfig
import app.zen.android.browser.SearchEngine
import app.zen.android.sync.SyncStatus
import kotlin.math.roundToInt

/**
 * Settings use the neutral app palette, never the active space's colours, and
 * mirror the parts of desktop's about:preferences that make sense on a phone.
 */
@Composable
fun SettingsScreen(vm: BrowserViewModel, dark: Boolean) {
    ZenTheme(dark = dark, accent = systemAccent()) {
        SettingsContent(vm)
    }
}

@Composable
private fun SettingsContent(vm: BrowserViewModel) {
    BackHandler { vm.showSettings = false }
    val prefs by vm.settings.state.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val lastSync by vm.lastSync.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    fun set(change: (Preferences) -> Preferences) = vm.settings.update(change)

    Surface(Modifier.fillMaxSize(), color = settingsBackground(), contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.showSettings = false }) { Icon(zenIcon("back"), "Back", Modifier.size(18.dp)) }
                Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Section("Account") {
                    val signedIn = status != SyncStatus.SignedOut
                    val subtitle = when (val s = status) {
                        SyncStatus.SignedOut -> "Sign in to sync your spaces, pinned tabs and folders with Zen desktop"
                        SyncStatus.Idle -> lastSync?.let { r ->
                            val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(r.at))
                            "Last synced at $time · ${r.received} received, ${r.sent} sent"
                        } ?: "Spaces are in sync"
                        SyncStatus.Syncing -> "Syncing…"
                        is SyncStatus.Failed -> "Sync error: ${s.message}"
                    }
                    SettingRow(if (signedIn) vm.accountEmail ?: "Mozilla account" else "Mozilla account", subtitle)
                    if (!signedIn) {
                        ActionRow("Sign in") {
                            vm.showSettings = false
                            vm.beginLogin()
                        }
                    } else {
                        ActionRow("Sync now") { vm.syncNow() }
                        ActionRow("Sign out", destructive = true) { vm.signOut() }
                    }
                }

                Section("General") {
                    DefaultBrowserRow()
                    ChoiceRow("Close temporary tabs", "Unpinned tabs opened on this phone, once unused", TabCleanup.entries, prefs.tabCleanup, { it.label }) { v -> set { it.copy(tabCleanup = v) } }
                    SwitchRow("Select the last used tab when closing one", null, prefs.selectRecentOnClose) { v -> set { it.copy(selectRecentOnClose = v) } }
                    SwitchRow("Glance", "Preview links from the long-press menu", prefs.glance) { v -> set { it.copy(glance = v) } }
                    SwitchRow("Haptic feedback", "Vibrate on long presses and while sliding across menu entries", prefs.haptics) { v -> set { it.copy(haptics = v) } }
                }

                Section("Search") {
                    SearchEngine.entries.forEach { engine ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { set { it.copy(searchEngine = engine) } }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(prefs.searchEngine == engine, null)
                            Text(engine.label, Modifier.padding(start = 8.dp))
                        }
                    }
                    SwitchRow("Show search suggestions", null, prefs.searchSuggestions) { v -> set { it.copy(searchSuggestions = v) } }
                    SwitchRow("Show search suggestions in private tabs", null, prefs.suggestionsInPrivate) { v -> set { it.copy(suggestionsInPrivate = v) } }
                    SwitchRow("Suggest from browsing history", null, prefs.historyInSuggestions) { v -> set { it.copy(historyInSuggestions = v) } }
                    SwitchRow("Suggest open tabs", null, prefs.openTabsInSuggestions) { v -> set { it.copy(openTabsInSuggestions = v) } }
                }

                Section("Look and Feel") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = prefs.themeMode == mode
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        set { it.copy(themeMode = mode) }
                                        vm.applyThemeMode(mode)
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(mode.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SwitchRow("Match status bar to website", "Tints the top of the screen with the page colour", prefs.tintStatusBar) { on ->
                        set { it.copy(tintStatusBar = on) }
                        if (!on) vm.pageTopColor = null else vm.requestCapture()
                    }
                    SwitchRow("Sidebar on the right", "Private tabs open from the other side", prefs.sidebarRight) { v -> set { it.copy(sidebarRight = v) } }
                    SliderRow("Text size", prefs.textScale) { v -> set { it.copy(textScale = v) } }
                    SwitchRow("Always allow zoom", "Pinch to zoom even when a site blocks it", prefs.forceZoom) { v -> set { it.copy(forceZoom = v) } }
                }

                Section("Privacy and Security") {
                    ChoiceRow("Enhanced Tracking Protection", prefs.tracking.detail, TrackingProtection.entries, prefs.tracking, { it.label }) { v -> set { it.copy(tracking = v) } }
                    SwitchRow("Block ads", "uBlock Origin, downloaded from Mozilla's add-on site and kept up to date", prefs.adBlocking) { v -> set { it.copy(adBlocking = v) } }
                    SwitchRow("Tell websites not to sell or share my data", "Global Privacy Control", prefs.globalPrivacyControl) { v -> set { it.copy(globalPrivacyControl = v) } }
                    SwitchRow("Remove tracking parameters from links", null, prefs.stripTrackingParams) { v -> set { it.copy(stripTrackingParams = v) } }
                    ChoiceRow("HTTPS-Only Mode", null, HttpsOnly.entries, prefs.httpsOnly, { it.label }) { v -> set { it.copy(httpsOnly = v) } }
                    ChoiceRow("DNS over HTTPS", prefs.dns.detail, DnsOverHttps.entries, prefs.dns, { it.label }) { v -> set { it.copy(dns = v) } }
                    SwitchRow("Remember browsing history", null, prefs.rememberHistory) { v -> set { it.copy(rememberHistory = v) } }
                    ActionRow("Clear browsing data", destructive = true) { confirmClear = true }
                    ActionRow("Reset site permissions") { vm.clearSitePermissions() }
                    ActionRow("Close all private tabs") { vm.closePrivateTabs() }
                }

                Section("Site Permissions") {
                    ChoiceRow("Autoplay", null, Autoplay.entries, prefs.autoplay, { it.label }) { v -> set { it.copy(autoplay = v) } }
                    ChoiceRow("Location", null, SitePermission.entries, prefs.location, { it.label }) { v -> set { it.copy(location = v) } }
                    ChoiceRow("Camera", null, SitePermission.entries, prefs.camera, { it.label }) { v -> set { it.copy(camera = v) } }
                    ChoiceRow("Microphone", null, SitePermission.entries, prefs.microphone, { it.label }) { v -> set { it.copy(microphone = v) } }
                    ChoiceRow("Notifications", null, SitePermission.entries, prefs.notifications, { it.label }) { v -> set { it.copy(notifications = v) } }
                }

                Section("Updates") {
                    val context = LocalContext.current
                    val update = vm.update
                    if (update != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UpdateDot()
                            Spacer(Modifier.width(10.dp))
                            SettingRow("Zen ${update.version} is available", "You have ${BuildConfig.VERSION_NAME}")
                        }
                        ActionRow(if (Updates.obtainiumInstalled(context)) "Update with Obtainium" else "Open the release page") {
                            if (!Updates.openObtainium(context)) {
                                vm.showSettings = false
                                vm.newTab(update.pageUrl)
                            }
                        }
                    }
                    ActionRow(if (vm.checkingUpdates) "Checking…" else "Check for updates now") { vm.checkForUpdates(force = true) }
                    SwitchRow("Check for updates automatically", "Asks GitHub twice a day and puts a yellow dot on the ⋯ button when a new version is out", prefs.checkUpdates) { v -> set { it.copy(checkUpdates = v) } }
                    SettingRow("Install updates with Obtainium", "Zen is not on the Play Store. Obtainium watches its GitHub releases and installs each new version, including GeckoView security fixes.")
                    ActionRow("Add Zen to Obtainium") {
                        if (!openLink(context, Updates.OBTAINIUM_ADD)) {
                            vm.message = "Install Obtainium first, then come back here"
                            vm.showSettings = false
                            vm.newTab(Updates.OBTAINIUM_DOWNLOAD)
                        }
                    }
                    CopyRow("Repository URL for Obtainium", Updates.REPO_URL) {
                        copyToClipboard(context, Updates.REPO_URL)
                        vm.message = "Repository URL copied"
                    }
                }

                Section("About") {
                    SettingRow("Zen for Android", "Version ${BuildConfig.VERSION_NAME} · GeckoView ${org.mozilla.geckoview.BuildConfig.MOZILLA_VERSION}")
                    ActionRow("Source code") {
                        vm.showSettings = false
                        vm.newTab("https://github.com/ericmatte/zen-browser-desktop")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear browsing data?") },
            text = { Text("History, cookies, cache and site data are removed. Your synced spaces and tabs stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearBrowsingData()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(settingsPanel())
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) { content() }
}

/** Panels stand clearly off the page, like grouped lists on iOS and Android settings. */
@Composable
private fun settingsBackground(): Color = if (MaterialTheme.colorScheme.background.isLight()) Color(0xFFE9E9EF) else Color(0xFF0C0C0F)

@Composable
private fun settingsPanel(): Color = if (MaterialTheme.colorScheme.background.isLight()) Color.White else Color(0xFF232329)

/**
 * Asks Android for the browser role. When the user already declined twice the
 * system answers instantly without a dialog, so we fall back to the default
 * apps screen.
 */
@Composable
private fun DefaultBrowserRow() {
    val context = LocalContext.current
    val roles = remember { context.getSystemService(RoleManager::class.java) }
    var isDefault by remember { mutableStateOf(roles?.isRoleHeld(RoleManager.ROLE_BROWSER) == true) }
    var askedAt by remember { mutableStateOf(0L) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = roles?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        if (!isDefault && System.currentTimeMillis() - askedAt < 400) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }
    if (isDefault) {
        SettingRow("Default browser", "Zen opens the links you tap in other apps")
    } else {
        ActionRow("Set Zen as default browser") {
            askedAt = System.currentTimeMillis()
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                request.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
            } else {
                context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun CopyRow(title: String, value: String, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onCopy).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(zenIcon("edit-copy"), "Copy", Modifier.padding(horizontal = 8.dp).size(18.dp))
    }
}

private fun openLink(context: Context, link: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

@Composable
private fun ActionRow(title: String, destructive: Boolean = false, onClick: () -> Unit) {
    Text(
        title,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 13.dp),
        color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange)
    }
}

@Composable
private fun <T> ChoiceRow(title: String, subtitle: String?, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(label(selected), Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(14.dp)) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, onChange: (Float) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("${(current * 100).roundToInt()}%", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Slider(current, { current = (it * 20).roundToInt() / 20f }, valueRange = 0.8f..2f, steps = 23, onValueChangeFinished = { onChange(current) })
    }
}
