package app.zen.android.browser

import org.json.JSONArray
import org.json.JSONObject

enum class DownloadStatus { RUNNING, DONE, FAILED }

/** One file the browser saved to the phone's Downloads folder. [uri] is its content:// address once created. */
data class DownloadItem(
    val id: String,
    val name: String,
    val mime: String?,
    val uri: String?,
    val total: Long,
    val received: Long,
    val status: DownloadStatus,
    val time: Long,
    val source: String,
) {
    /** 0..1 while the size is known; null when the server did not say how big the file is. */
    val progress: Float? get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else null
}

object Downloads {
    const val MAX = 200

    /** Newest first; a download that restarts under the same id replaces its old entry. */
    fun upsert(list: List<DownloadItem>, item: DownloadItem): List<DownloadItem> =
        (listOf(item) + list.filter { it.id != item.id }).take(MAX)

    /** Downloads cut off by the app closing never finish; they show as failed instead of spinning forever. */
    fun afterRestart(list: List<DownloadItem>): List<DownloadItem> =
        list.map { if (it.status == DownloadStatus.RUNNING) it.copy(status = DownloadStatus.FAILED) else it }

    fun size(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    fun encode(list: List<DownloadItem>): String = JSONArray().also { arr ->
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("mime", it.mime ?: JSONObject.NULL).put("uri", it.uri ?: JSONObject.NULL)
                    .put("total", it.total).put("received", it.received).put("status", it.status.name).put("time", it.time).put("source", it.source),
            )
        }
    }.toString()

    fun decode(json: String?): List<DownloadItem> = runCatching {
        val arr = JSONArray(json ?: return emptyList())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DownloadItem(
                o.getString("id"),
                o.getString("name"),
                o.optString("mime").takeUnless { o.isNull("mime") || it.isEmpty() },
                o.optString("uri").takeUnless { o.isNull("uri") || it.isEmpty() },
                o.optLong("total"),
                o.optLong("received"),
                runCatching { DownloadStatus.valueOf(o.getString("status")) }.getOrDefault(DownloadStatus.FAILED),
                o.optLong("time"),
                o.optString("source"),
            )
        }
    }.getOrDefault(emptyList())
}
