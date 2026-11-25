package app.zen.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zen.android.browser.ActivityBridge

class MainActivity : ComponentActivity(), ActivityBridge {
    private val vm: BrowserViewModel by viewModels()

    private var permissionResult: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        permissionResult?.invoke(granted.isNotEmpty() && granted.values.all { it })
        permissionResult = null
    }

    private var singleFileResult: ((List<Uri>) -> Unit)? = null
    private val singleFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        singleFileResult?.invoke(listOfNotNull(uri))
        singleFileResult = null
    }
    private val multiFileLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        singleFileResult?.invoke(uris)
        singleFileResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !vm.ready }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        vm.requests.bridge = this
        if (savedInstanceState == null) handle(intent)
        setContent {
            val prefs by vm.settings.state.collectAsStateWithLifecycle()
            val dark = when (prefs.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            } || vm.privateMode
            val spaceAccent = vm.space(vm.activeSpace)?.theme.accent()
            ZenTheme(dark = dark, accent = spaceAccent ?: systemAccent()) {
                val prefs by vm.settings.state.collectAsStateWithLifecycle()
                val haptics = LocalHapticFeedback.current
                CompositionLocalProvider(LocalHapticFeedback provides if (prefs.haptics) haptics else NoHaptics) {
                    BrowserScreen(vm, dark)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_NEW_PRIVATE_TAB) {
            vm.requestPrivateBrowsing()
            return
        }
        val text = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra("query")
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        if (!text.isNullOrBlank()) vm.openIncoming(text)
    }

    override fun onStart() {
        super.onStart()
        vm.startSyncLoop()
    }

    override fun onStop() {
        vm.stopSyncLoop()
        super.onStop()
    }

    override fun requestPermissions(permissions: Array<String>, result: (Boolean) -> Unit) {
        permissionResult = result
        permissionLauncher.launch(permissions)
    }

    override fun pickFiles(mimeTypes: Array<String>, multiple: Boolean, result: (List<Uri>) -> Unit) {
        singleFileResult = result
        if (multiple) multiFileLauncher.launch(mimeTypes) else singleFileLauncher.launch(mimeTypes)
    }
}

const val ACTION_NEW_PRIVATE_TAB = "app.zen.android.action.NEW_PRIVATE_TAB"
