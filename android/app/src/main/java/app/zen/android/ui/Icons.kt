package app.zen.android.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
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
            val fillType = if (entry.optBoolean("evenodd")) PathFillType.EvenOdd else PathFillType.NonZero
            val translate = entry.optJSONArray("translate")
            // Lucide icons (e.g. desktop's downloads archive.svg) are outlines drawn with a stroke.
            val stroke = entry.optDouble("stroke", 0.0).toFloat()
            fun nodes(d: String) = PathParser().parsePathString(d).toNodes()
            ImageVector.Builder(name, 24.dp, 24.dp, viewport, viewport).apply {
                // Desktop SVGs with an offset viewBox (e.g. "4 4 24 24") carry the offset as a translation.
                addGroup(translationX = translate?.getDouble(0)?.toFloat() ?: 0f, translationY = translate?.getDouble(1)?.toFloat() ?: 0f)
                for (i in 0 until paths.length()) {
                    val clipped = paths.optJSONObject(i)
                    if (clipped != null) {
                        addGroup(clipPathData = nodes(clipped.getString("clip")))
                        addPath(nodes(clipped.getString("d")), fillType, fill = SolidColor(Color.Black))
                        clearGroup()
                    } else if (stroke > 0f) {
                        addPath(
                            nodes(paths.getString(i)),
                            stroke = SolidColor(Color.Black),
                            strokeLineWidth = stroke,
                            strokeLineCap = StrokeCap.Round,
                            strokeLineJoin = StrokeJoin.Round,
                        )
                    } else {
                        addPath(nodes(paths.getString(i)), fillType, fill = SolidColor(Color.Black))
                    }
                }
                clearGroup()
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

    private val tones = java.util.WeakHashMap<ImageBitmap, Float>()

    /** Measured once per bitmap; favicons are small (at most 64 px), so this stays cheap. */
    @Synchronized
    fun luminance(image: ImageBitmap): Float? = tones[image] ?: run {
        val bitmap = image.asAndroidBitmap().let { if (it.config == android.graphics.Bitmap.Config.HARDWARE) it.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else it }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        app.zen.android.browser.FaviconTone.luminance(pixels)?.also { tones[image] = it }
    }

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

/** A thin halo traced from the icon's own shape, so a black logo on a dark sidebar (or a white one on a light one) stays visible. */
private fun Modifier.contrastOutline(image: ImageBitmap, color: Color): Modifier = drawBehind {
    val step = 1.dp.toPx()
    val tint = ColorFilter.tint(color)
    val box = IntSize(size.width.toInt(), size.height.toInt())
    for ((dx, dy) in listOf(-step to 0f, step to 0f, 0f to -step, 0f to step)) {
        drawImage(image, dstOffset = IntOffset(dx.toInt(), dy.toInt()), dstSize = box, colorFilter = tint)
    }
}

@Composable
fun Favicon(url: String, dataIcon: String?, size: Dp, modifier: Modifier = Modifier, loading: Boolean = false, spinning: Boolean = false) {
    val context = LocalContext.current
    val key = Favicons.host(url) ?: url
    var bitmap by remember(key, dataIcon) { mutableStateOf(if (dataIcon == null) Favicons.cached(key) else null) }
    // Tried again once the page settles: many sites only serve their icon after the page itself.
    LaunchedEffect(key, dataIcon, loading) { if (bitmap == null) bitmap = Favicons.load(context, url, dataIcon) }
    val current = bitmap
    val darkSurface = MaterialTheme.colorScheme.onSurface.isLight()
    val blendsIn = current?.let { Favicons.luminance(it) }?.let { app.zen.android.browser.FaviconTone.blendsIn(it, darkSurface) } == true
    if (current != null && !spinning) {
        Image(
            current,
            null,
            modifier
                .size(size)
                .then(if (blendsIn) Modifier.contrastOutline(current, if (darkSurface) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.35f)) else Modifier)
                .clip(RoundedCornerShape(size / 5)),
        )
    } else if (loading || spinning) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(size * 0.8f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), strokeWidth = 2.dp)
        }
    } else {
        // Desktop falls back to a globe for pages without a favicon.
        val globe = remember { ZenIconSet.get(context, "globe") }
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            if (globe != null) {
                Icon(globe, null, Modifier.size(size * 0.95f), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            } else {
                Text(UrlInput.display(url).take(1).uppercase(), fontSize = (size.value * 0.55f).sp)
            }
        }
    }
}

/** The brand colour of a page's favicon, used to outline a selected essential. */
@Composable
fun rememberFaviconAccent(url: String, dataIcon: String?): Color? {
    val context = LocalContext.current
    var accent by remember(url, dataIcon) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url, dataIcon) {
        val bitmap = Favicons.load(context, url, dataIcon) ?: return@LaunchedEffect
        accent = withContext(Dispatchers.Default) {
            val android = bitmap.asAndroidBitmap().let { if (it.config == android.graphics.Bitmap.Config.HARDWARE) it.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else it }
            val pixels = IntArray(android.width * android.height)
            android.getPixels(pixels, 0, android.width, 0, 0, android.width, android.height)
            app.zen.android.browser.PageColor.accent(pixels)?.let { Color(it) }
        }
    }
    return accent
}
