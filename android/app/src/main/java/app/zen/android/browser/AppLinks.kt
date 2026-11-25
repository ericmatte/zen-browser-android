package app.zen.android.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri

class NativeApp(val packageName: String, val label: String, val icon: Drawable?)

/**
 * Finds an installed app (YouTube, Maps, Spotify…) that claims a web address,
 * so the page can be handed over to it. Browsers claim every address, so an
 * app only counts when it is not one of them.
 */
object AppLinks {
    /** The first handler that is neither a browser nor this app. */
    fun pick(handlers: List<String>, browsers: Set<String>, self: String): String? =
        handlers.firstOrNull { it != self && it !in browsers }

    fun find(context: Context, url: String): NativeApp? {
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val pm = context.packageManager
        val browsers = handlers(pm, "https://example.invalid/").toSet()
        val pkg = pick(handlers(pm, url), browsers, context.packageName) ?: return null
        return runCatching {
            val info = pm.getApplicationInfo(pkg, 0)
            NativeApp(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
        }.getOrNull()
    }

    fun open(context: Context, url: String, app: NativeApp): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private fun handlers(pm: PackageManager, url: String): List<String> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val found = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        return found
            .map { it.activityInfo.packageName }
            .distinct()
    }
}
