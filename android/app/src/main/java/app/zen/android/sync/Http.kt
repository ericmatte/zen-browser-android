package app.zen.android.sync

import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val status: Int, val body: String, val headers: Map<String, String>) {
    val ok get() = status in 200..299
}

class HttpException(val status: Int, body: String) : Exception("HTTP $status: ${body.take(300)}")

fun interface HttpTransport {
    fun request(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse
}

object UrlConnectionTransport : HttpTransport {
    override fun request(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val responseHeaders = conn.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key.lowercase() }
                .mapValues { it.value.firstOrNull() ?: "" }
            return HttpResponse(status, text, responseHeaders)
        } finally {
            conn.disconnect()
        }
    }
}
