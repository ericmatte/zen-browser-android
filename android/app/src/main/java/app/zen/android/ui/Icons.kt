package app.zen.android.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zen.android.browser.UrlInput
import app.zen.android.sync.ZenIcon
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Zen desktop's own SVG icons: the "selectable" space/folder icons and the
 * nucleo UI icons, extracted from src/browser/themes/shared/zen-icons.
 */
object ZenIconSet {
    private val cache = mutableMapOf<String, Map<String, ImageVector>>()

    fun get(context: Context, name: String): ImageVector? = load(context, "zen-icons.json")[name]

    fun ui(context: Context, name: String): ImageVector = load(context, "ui-icons.json").getValue(name)

    @Synchronized
    private fun load(context: Context, asset: String): Map<String, ImageVector> = cache.getOrPut(asset) {
        val json = JSONObject(context.assets.open(asset).bufferedReader().use { it.readText() })
        json.keys().asSequence().associateWith { name ->
            val entry = json.getJSONObject(name)
            val paths = entry.getJSONArray("d")
            val viewport = entry.optInt("size", 512).toFloat()
            ImageVector.Builder(name, 24.dp, 24.dp, viewport, viewport).apply {
                for (i in 0 until paths.length()) {
                    addPath(
                        pathData = PathParser().parsePathString(paths.getString(i)).toNodes(),
                        pathFillType = if (entry.optBoolean("evenodd")) PathFillType.EvenOdd else PathFillType.NonZero,
                        fill = SolidColor(Color.Black),
                    )
                }
            }.build()
        }
    }
}

/** UI icon from Zen's desktop icon set, e.g. `zenIcon("edit-copy")`. */
@Composable
fun zenIcon(name: String): ImageVector {
    val context = LocalContext.current
    return remember(name) { ZenIconSet.ui(context, name) }
}

@Composable
fun ZenIconView(icon: ZenIcon?, size: Dp, tint: Color, fallback: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    when (icon) {
        is ZenIcon.Emoji -> Text(icon.text, fontSize = (size.value * 0.85f).sp, lineHeight = (size.value).sp)
        is ZenIcon.Bundled -> {
            val vector = remember(icon.name) { ZenIconSet.get(context, icon.name) }
            if (vector != null) Icon(vector, null, Modifier.size(size), tint = tint) else fallback?.invoke()
        }
        null -> fallback?.invoke()
    }
}

/**
 * Desktop only syncs `data:` favicons (remote ones are dropped by
 * syncableIconUrl), so most tabs arrive without one. Missing icons are
 * fetched once per host and cached on disk, which keeps the sidebar instant.
 */
object Favicons {
    private val memory = LruCache<String, ImageBitmap>(256)
    private val failed = mutableSetOf<String>()

    fun host(url: String): String? = runCatching { URL(url).host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }

    fun serviceUrl(host: String) = "https://www.google.com/s2/favicons?domain=$host&sz=64"

    fun cached(key: String): ImageBitmap? = memory.get(key)

    suspend fun load(context: Context, pageUrl: String, dataIcon: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        dataIcon?.let { decodeDataUrl(it) }?.let { return@withContext it }
        val host = host(pageUrl) ?: return@withContext null
        memory.get(host)?.let { return@withContext it }
        if (host in failed) return@withContext null
        val file = File(File(context.cacheDir, "favicons").apply { mkdirs() }, sha1(host))
        val bytes = if (file.exists()) file.readBytes() else runCatching { download(serviceUrl(host)) }.getOrNull()?.also { file.writeBytes(it) }
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.takeIf { it.width > 1 }?.asImageBitmap()
        if (bitmap == null) failed += host else memory.put(host, bitmap)
        bitmap
    }

    private fun decodeDataUrl(data: String): ImageBitmap? = runCatching {
        if (!data.contains(";base64,")) return null
        val bytes = Base64.decode(data.substringAfter(";base64,"), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()

    private fun download(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        return try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha1(text: String) = MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

@Composable
fun Favicon(url: String, dataIcon: String?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = Favicons.host(url) ?: url
    var bitmap by remember(key, dataIcon) { mutableStateOf(if (dataIcon == null) Favicons.cached(key) else null) }
    LaunchedEffect(key, dataIcon) { if (bitmap == null) bitmap = Favicons.load(context, url, dataIcon) }
    val current = bitmap
    if (current != null) {
        Image(current, null, modifier.size(size).clip(RoundedCornerShape(size / 5)))
    } else {
        Box(
            modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                UrlInput.display(url).take(1).uppercase(),
                fontSize = (size.value * 0.55f).sp,
                lineHeight = (size.value * 0.6f).sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}
