package app.zen.android.sync

import org.json.JSONObject
import java.net.URLEncoder

const val OLDSYNC_SCOPE = "https://identity.mozilla.com/apps/oldsync"

/**
 * Uses the public Firefox for Android OAuth client, the same one community
 * Fenix forks rely on, because Mozilla does not hand out Sync-capable client
 * ids to third parties.
 */
object FxaConfig {
    const val CLIENT_ID = "a2270f727f45f648"
    const val REDIRECT_URI = "https://accounts.firefox.com/oauth/success/$CLIENT_ID"
    const val CONTENT_URL = "https://accounts.firefox.com"
    const val AUTH_API = "https://api.accounts.firefox.com/v1"
    const val TOKEN_SERVER = "https://token.services.mozilla.com/1.0/sync/1.5"
    const val PROFILE_API = "https://profile.accounts.firefox.com/v1"
}

data class SyncCredentials(
    val refreshToken: String,
    val keyId: String,
    val syncKey: ByteArray,
)

data class AccessToken(val token: String, val expiresAtMillis: Long)

class PendingLogin(
    val state: String = B64.url(randomBytes(16)),
    val pkce: Pkce = Pkce(),
    val keys: ScopedKeysExchange = ScopedKeysExchange(),
) {
    fun authorizationUrl(): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val params = linkedMapOf(
            "client_id" to FxaConfig.CLIENT_ID,
            "redirect_uri" to FxaConfig.REDIRECT_URI,
            "scope" to "profile $OLDSYNC_SCOPE",
            "state" to state,
            "response_type" to "code",
            "access_type" to "offline",
            "code_challenge" to pkce.challenge,
            "code_challenge_method" to "S256",
            "keys_jwk" to B64.url(keys.publicJwk.toByteArray(Charsets.UTF_8)),
            "action" to "email",
        )
        return "${FxaConfig.CONTENT_URL}/authorization?" +
            params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
    }
}

class FxaAuth(private val http: HttpTransport = UrlConnectionTransport) {

    fun isRedirect(url: String) = url.startsWith(FxaConfig.REDIRECT_URI)

    fun completeLogin(pending: PendingLogin, redirectUrl: String): SyncCredentials {
        val query = java.net.URI(redirectUrl).rawQuery ?: throw IllegalStateException("No query in redirect")
        val params = query.split('&').associate {
            val (k, v) = it.split('=', limit = 2).let { p -> p[0] to p.getOrElse(1) { "" } }
            k to java.net.URLDecoder.decode(v, "UTF-8")
        }
        if (params["state"] != pending.state) throw IllegalStateException("OAuth state mismatch")
        val code = params["code"] ?: throw IllegalStateException("Missing OAuth code: ${params["error"]}")

        val body = JSONObject()
            .put("client_id", FxaConfig.CLIENT_ID)
            .put("grant_type", "authorization_code")
            .put("code", code)
            .put("code_verifier", pending.pkce.verifier)
        val json = postJson("${FxaConfig.AUTH_API}/oauth/token", body)
        val refresh = json.optString("refresh_token").ifEmpty { throw IllegalStateException("No refresh token") }
        val keysJwe = json.optString("keys_jwe").ifEmpty { throw IllegalStateException("No Sync keys returned") }
        return credentialsFrom(refresh, pending.keys.decrypt(keysJwe))
    }

    /** The signed-in account's email, read with a short-lived `profile` token. */
    fun email(credentials: SyncCredentials): String? {
        val token = accessToken(credentials, scope = "profile").token
        val res = http.request("GET", "${FxaConfig.PROFILE_API}/profile", mapOf("Authorization" to "Bearer $token"), null)
        if (!res.ok) throw HttpException(res.status, res.body)
        return JSONObject(res.body).optString("email").ifEmpty { null }
    }

    fun accessToken(credentials: SyncCredentials, scope: String = OLDSYNC_SCOPE): AccessToken {
        val body = JSONObject()
            .put("client_id", FxaConfig.CLIENT_ID)
            .put("grant_type", "refresh_token")
            .put("refresh_token", credentials.refreshToken)
            .put("scope", scope)
        val json = postJson("${FxaConfig.AUTH_API}/oauth/token", body)
        return AccessToken(
            json.getString("access_token"),
            System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
        )
    }

    private fun postJson(url: String, body: JSONObject): JSONObject {
        val res = http.request("POST", url, mapOf("Content-Type" to "application/json"), body.toString())
        if (!res.ok) throw HttpException(res.status, res.body)
        return JSONObject(res.body)
    }

    companion object {
        fun credentialsFrom(refreshToken: String, scopedKeysJson: String): SyncCredentials {
            val key = JSONObject(scopedKeysJson).getJSONObject(OLDSYNC_SCOPE)
            return SyncCredentials(refreshToken, key.getString("kid"), B64.fromUrl(key.getString("k")))
        }
    }
}
