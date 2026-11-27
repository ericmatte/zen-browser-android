package app.zen.android.sync

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncClientTest {
    private val syncKey = ByteArray(64) { it.toByte() }
    private val root = KeyBundle.fromScopedKey(syncKey)

    /** A storage server that holds one keyring and one collection, and records every upload. */
    private inner class FakeServer : HttpTransport {
        var collectionKey = bundle(1)
        var records = listOf<Pair<String, String>>()
        val uploads = mutableListOf<Int>()

        fun bundle(seed: Int) = KeyBundle(ByteArray(32) { (it + seed).toByte() }, ByteArray(32) { (it * seed).toByte() })

        override fun request(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse = when {
            url.endsWith("/oauth/token") -> ok(JSONObject().put("access_token", "a").put("expires_in", 3600))
            url.startsWith(FxaConfig.TOKEN_SERVER) -> ok(
                JSONObject().put("id", "id").put("key", "key").put("api_endpoint", "https://storage.test/1.5/1").put("duration", 3600),
            )
            url.endsWith("storage/crypto/keys") -> {
                val keyring = JSONObject().put("default", JSONArray().put(B64.std(collectionKey.encKey)).put(B64.std(collectionKey.hmacKey)))
                ok(JSONObject().put("id", "keys").put("payload", root.encrypt(keyring.toString())))
            }
            method == "GET" -> HttpResponse(
                200,
                JSONArray().apply { records.forEach { (id, payload) -> put(JSONObject().put("id", id).put("payload", payload)) } }.toString(),
                mapOf("x-last-modified" to "42.0"),
            )
            else -> {
                uploads += JSONArray(body).length()
                ok(JSONObject().put("modified", 50.0).put("success", JSONArray()).put("failed", JSONObject()))
            }
        }

        private fun ok(json: JSONObject) = HttpResponse(200, json.toString(), emptyMap())
    }

    private fun client(server: FakeServer) = SyncClient(SyncCredentials("refresh", "kid", syncKey), FxaAuth(server), server)

    @Test
    fun `uploads are split into posts of at most 100 records`() {
        val server = FakeServer()
        val modified = client(server).upload(SPACES_COLLECTION, (1..250).map { "r$it" to JSONObject().put("id", "r$it") })
        assertEquals(listOf(100, 100, 50), server.uploads)
        assertEquals(50.0, modified, 0.0)
    }

    @Test
    fun `fetch reloads the keys after desktop re-keyed sync`() {
        val server = FakeServer()
        val client = client(server)
        server.records = listOf("a" to server.collectionKey.encrypt("""{"id":"a"}"""))
        assertEquals(listOf("a"), client.fetch(SPACES_COLLECTION).first.map { it.id })

        server.collectionKey = server.bundle(7)
        server.records = listOf("b" to server.collectionKey.encrypt("""{"id":"b"}"""))
        assertEquals(listOf("b"), client.fetch(SPACES_COLLECTION).first.map { it.id })
    }

    @Test
    fun `a record that cannot be read is skipped instead of failing the sync`() {
        val server = FakeServer()
        server.records = listOf(
            "good" to server.collectionKey.encrypt("""{"id":"good"}"""),
            "bad" to server.bundle(9).encrypt("""{"id":"bad"}"""),
        )
        val (records, modified) = client(server).fetch(SPACES_COLLECTION)
        assertEquals(listOf("good"), records.map { it.id })
        assertEquals(42.0, modified, 0.0)
    }
}
