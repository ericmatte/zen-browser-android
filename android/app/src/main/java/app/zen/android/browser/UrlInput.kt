package app.zen.android.browser

import org.json.JSONArray
import java.net.URLEncoder

enum class SearchEngine(val label: String, val home: String, private val query: String) {
    GOOGLE("Google", "https://www.google.com/", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/", "https://duckduckgo.com/?q="),
    BING("Bing", "https://www.bing.com/", "https://www.bing.com/search?q="),
    BRAVE("Brave Search", "https://search.brave.com/", "https://search.brave.com/search?q="),
    ECOSIA("Ecosia", "https://www.ecosia.org/", "https://www.ecosia.org/search?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/", "https://www.startpage.com/do/search?q=");

    fun search(text: String) = query + URLEncoder.encode(text, "UTF-8")

    fun suggestUrl(text: String): String {
        val q = URLEncoder.encode(text, "UTF-8")
        return if (this == GOOGLE) "https://suggestqueries.google.com/complete/search?client=firefox&q=$q" else "https://duckduckgo.com/ac/?type=list&q=$q"
    }
}

object UrlInput {
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val hostLike = Regex("^(localhost|[\\w-]+(\\.[\\w-]+)+|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$")

    fun resolve(input: String, engine: SearchEngine = SearchEngine.GOOGLE): String {
        val text = input.trim()
        return when {
            text.isEmpty() -> engine.home
            text.contains(' ') -> engine.search(text)
            scheme.containsMatchIn(text) && !hostLike.matches(text) -> text
            hostLike.matches(text) -> "https://$text"
            else -> engine.search(text)
        }
    }

    /** True when [input] would be sent to the search engine rather than opened as an address. */
    fun isSearch(input: String, engine: SearchEngine = SearchEngine.GOOGLE): Boolean {
        val text = input.trim()
        return text.isNotEmpty() && resolve(text, engine) == engine.search(text)
    }

    /** Both Google (client=firefox) and DuckDuckGo (type=list) answer with the OpenSearch shape `[query, [suggestions…]]`. */
    fun parseSuggestions(body: String, limit: Int = 5): List<String> = runCatching {
        val list = JSONArray(body).getJSONArray(1)
        (0 until list.length()).map { list.getString(it) }.filter { it.isNotBlank() }.take(limit)
    }.getOrDefault(emptyList())

    fun display(url: String): String =
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/').ifEmpty { url }
}
