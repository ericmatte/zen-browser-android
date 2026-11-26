package app.zen.android.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * A picture of the page on screen when the app went to the background. At
 * the next cold start it is shown at once while Gecko loads the real page
 * underneath, so reopening the app looks instant. One picture is kept, and
 * never for private tabs.
 */
class PageSnapshot(private val dir: File) {
    private val image get() = File(dir, "page.webp")
    private val owner get() = File(dir, "page.tab")

    fun save(tabId: String, bitmap: Bitmap) {
        if (isPrivateTab(tabId)) return clear()
        dir.mkdirs()
        val tmp = File(dir, "page.webp.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }
        tmp.renameTo(image)
        owner.writeText(tabId)
    }

    /** The saved picture, if it belongs to [tabId]. */
    fun load(tabId: String): Bitmap? {
        if (!owner.exists() || owner.readText() != tabId || !image.exists()) return null
        return runCatching { BitmapFactory.decodeFile(image.path) }.getOrNull()
    }

    fun clear() {
        image.delete()
        owner.delete()
    }
}
