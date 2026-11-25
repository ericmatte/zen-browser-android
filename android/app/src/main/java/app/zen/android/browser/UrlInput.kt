package app.zen.android.browser

import java.net.URLEncoder

object UrlInput {
    private const val SEARCH = "https://duckduckgo.com/?q="
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val hostLike = Regex("^(localhost|[\\w-]+(\\.[\\w-]+)+|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$")

    fun resolve(input: String): String {
        val text = input.trim()
        return when {
            text.isEmpty() -> "about:blank"
            text.contains(' ') -> SEARCH + URLEncoder.encode(text, "UTF-8")
            scheme.containsMatchIn(text) && !hostLike.matches(text) -> text
            hostLike.matches(text) -> "https://$text"
            else -> SEARCH + URLEncoder.encode(text, "UTF-8")
        }
    }

    fun display(url: String): String =
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/').ifEmpty { url }
}
