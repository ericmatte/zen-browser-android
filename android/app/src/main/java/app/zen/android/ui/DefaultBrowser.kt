package app.zen.android.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Whether Zen is the default browser, and the system prompt that makes it so. Shared by Settings and the welcome screen. */
class DefaultBrowser internal constructor(private val context: Context, private val roles: RoleManager?) {
    var isDefault by mutableStateOf(roles?.isRoleHeld(RoleManager.ROLE_BROWSER) == true)
        internal set
    internal var askedAt by mutableLongStateOf(0L)
    internal lateinit var launcher: ManagedActivityResultLauncher<Intent, ActivityResult>

    fun ask() {
        askedAt = System.currentTimeMillis()
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
            launcher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
        } else {
            openSystemSettings()
        }
    }

    internal fun onResult() {
        isDefault = roles?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        // The role prompt closes at once when Android will not show it again; the system list is the way left.
        if (!isDefault && System.currentTimeMillis() - askedAt < 400) openSystemSettings()
    }

    private fun openSystemSettings() = context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
}

@Composable
fun rememberDefaultBrowser(): DefaultBrowser {
    val context = LocalContext.current
    val state = remember { DefaultBrowser(context, context.getSystemService(RoleManager::class.java)) }
    state.launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { state.onResult() }
    return state
}
