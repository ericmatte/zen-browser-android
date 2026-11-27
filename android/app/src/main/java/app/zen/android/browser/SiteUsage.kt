package app.zen.android.browser

import java.util.Locale

object SiteUsage {
    fun label(bytes: Long): String = when {
        bytes < 1_000 -> "$bytes B"
        bytes < 1_000_000 -> "${(bytes + 500) / 1_000} kB"
        else -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    }
}
