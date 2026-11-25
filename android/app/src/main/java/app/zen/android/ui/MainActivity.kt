package app.zen.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    private val vm: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    vm.loginSession != null -> vm.cancelLogin()
                    vm.goBack() -> Unit
                    else -> moveTaskToBack(true)
                }
            }
        })
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) zenDark else zenLight) {
                BrowserScreen(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.startSyncLoop()
    }

    override fun onStop() {
        vm.stopSyncLoop()
        super.onStop()
    }
}

private val zenAccent = Color(0xFFF76F53)
private val zenDark = darkColorScheme(
    primary = zenAccent,
    background = Color(0xFF1B1B1F),
    surface = Color(0xFF232327),
    surfaceVariant = Color(0xFF2E2E33),
)
private val zenLight = lightColorScheme(
    primary = zenAccent,
    background = Color(0xFFF2F0F3),
    surface = Color(0xFFFAF9FB),
    surfaceVariant = Color(0xFFE8E6EA),
)
