package app.zen.android.sync

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/** Matches the desktop engine name "Spaces" (ZenSpacesSync.sys.mjs). */
const val SPACES_COLLECTION = "spaces"

data class StorageToken(val id: String, val key: String, val endpoint: String, val expiresAtMillis: Long)

data class Bso(val id: String, val cleartext: JSONObject?, val modified: Double) {
    val deleted get() = cleartext == null || cleartext.optBoolean("deleted")
}

class SyncClient(
    private val credentials: SyncCredentials,
    private val auth: FxaAuth = FxaAuth(),
    private val http: HttpTransport = UrlConnectionTransport,
) {
    private var token: StorageToken? = null
    private var keys: Map<String, KeyBundle>? = null
    private var defaultKey: KeyBundle? = null

    private fun storageToken(): StorageToken {
        token?.takeIf { it.expiresAtMillis > System.currentTimeMillis() + 60_000 }?.let { return it }
        val access = auth.accessToken(credentials)
        val res = http.request(
            "GET", FxaConfig.TOKEN_SERVER,
            mapOf("Authorization" to "Bearer ${access.token}", "X-KeyID" to credentials.keyId),
            null,
        )
        if (!res.ok) throw HttpException(res.status, res.body)
        val json = JSONObject(res.body)
        return StorageToken(
            json.getString("id"),
            json.getString("key"),
            json.getString("api_endpoint"),
            System.currentTimeMillis() + json.optLong("duration", 3600) * 1000,
        ).also { token = it }
    }

    private fun storage(method: String, path: String, body: String? = null, extra: Map<String, String> = emptyMap()): HttpResponse {
        val t = storageToken()
        val url = "${t.endpoint}/$path"
        val headers = buildMap {
            put("Authorization", Hawk.header(t.id, t.key, method, URL(url)))
            put("Accept", "application/json")
            if (body != null) put("Content-Type", "application/json")
            putAll(extra)
        }
        val res = http.request(method, url, headers, body)
        if (res.status == 401) token = null
        if (!res.ok) throw HttpException(res.status, res.body)
        return res
    }

    private fun keyFor(collection: String): KeyBundle {
        if (defaultKey == null) {
            val root = KeyBundle.fromScopedKey(credentials.syncKey)
            val bso = JSONObject(storage("GET", "storage/crypto/keys").body)
            val keysJson = JSONObject(root.decrypt(bso.getString("payload")))
            defaultKey = bundleFrom(keysJson.getJSONArray("default"))
            val perCollection = keysJson.optJSONObject("collections") ?: JSONObject()
            keys = perCollection.keys().asSequence().associateWith { bundleFrom(perCollection.getJSONArray(it)) }
        }
        return keys?.get(collection) ?: defaultKey!!
    }

    fun fetch(collection: String, newerThan: Double = 0.0): Pair<List<Bso>, Double> {
        val res = storage("GET", "storage/$collection?full=1&newer=$newerThan")
        val lastModified = res.headers["x-last-modified"]?.toDoubleOrNull() ?: newerThan
        val array = JSONArray(res.body)
        val raws = (0 until array.length()).map { array.getJSONObject(it) }
        var key = keyFor(collection)
        fun open(raw: JSONObject) = runCatching { Bso(raw.getString("id"), JSONObject(key.decrypt(raw.getString("payload"))), raw.optDouble("modified", 0.0)) }.getOrNull()
        var records = raws.map(::open)
        // Desktop re-keys when Sync is reset; the cached keys then fail on every record until they are fetched again.
        if (records.any { it == null }) {
            defaultKey = null
            keys = null
            key = keyFor(collection)
            records = raws.map(::open)
        }
        // A record that still cannot be read is skipped, so one bad record never blocks the rest of the sync.
        return records.filterNotNull() to lastModified
    }

    fun upload(collection: String, records: List<Pair<String, JSONObject>>): Double {
        if (records.isEmpty()) return 0.0
        val key = keyFor(collection)
        var modified = 0.0
        // The storage server refuses a POST of more than 100 records.
        records.chunked(100).forEach { chunk ->
            val body = JSONArray()
            chunk.forEach { (id, cleartext) ->
                body.put(JSONObject().put("id", id).put("payload", key.encrypt(cleartext.toString())))
            }
            val res = JSONObject(storage("POST", "storage/$collection", body.toString()).body)
            val failed = res.optJSONObject("failed")
            if (failed != null && failed.length() > 0) throw IllegalStateException("Sync rejected: $failed")
            modified = res.optDouble("modified", modified)
        }
        return modified
    }

    private fun bundleFrom(pair: JSONArray) = KeyBundle(B64.fromStd(pair.getString(0)), B64.fromStd(pair.getString(1)))
}
