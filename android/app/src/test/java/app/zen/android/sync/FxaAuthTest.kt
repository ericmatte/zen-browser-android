package app.zen.android.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FxaAuthTest {

    @Test
    fun `authorization url requests offline Sync scope with PKCE and keys_jwk`() {
        val pending = PendingLogin()
        val url = pending.authorizationUrl()
        assertTrue(url.startsWith("https://accounts.firefox.com/authorization?"))
        assertTrue(url.contains("access_type=offline"))
        assertTrue(url.contains("code_challenge=${pending.pkce.challenge}"))
        assertTrue(url.contains(java.net.URLEncoder.encode(OLDSYNC_SCOPE, "UTF-8")))
        assertTrue(url.contains("keys_jwk="))
    }

    @Test
    fun `credentials are read from the decrypted scoped keys bundle`() {
        val k = ByteArray(64) { 7 }
        val json = JSONObject().put(OLDSYNC_SCOPE, JSONObject().put("kid", "1234-abc").put("k", B64.url(k))).toString()
        val creds = FxaAuth.credentialsFrom("refresh", json)
        assertEquals("1234-abc", creds.keyId)
        assertTrue(k.contentEquals(creds.syncKey))
    }

    @Test(expected = IllegalStateException::class)
    fun `redirect with a foreign state is refused`() {
        val auth = FxaAuth { _, _, _, _ -> error("must not hit the network") }
        auth.completeLogin(PendingLogin(), "${FxaConfig.REDIRECT_URI}?code=abc&state=forged")
    }

    @Test
    fun `redirect detection only matches the registered redirect uri`() {
        val auth = FxaAuth()
        assertTrue(auth.isRedirect("${FxaConfig.REDIRECT_URI}?code=1&state=2"))
        assertTrue(!auth.isRedirect("https://accounts.firefox.com/signin"))
    }

    @Test
    fun `email is read from the profile endpoint with a profile scoped token`() {
        val calls = mutableListOf<String>()
        val auth = FxaAuth { method, url, headers, body ->
            calls += "$method $url ${headers["Authorization"].orEmpty()} ${body.orEmpty()}"
            if (url.endsWith("/oauth/token")) {
                HttpResponse(200, JSONObject().put("access_token", "tok").put("expires_in", 60).toString(), emptyMap())
            } else {
                HttpResponse(200, JSONObject().put("email", "me@example.com").toString(), emptyMap())
            }
        }
        val email = auth.email(SyncCredentials("refresh", "kid", ByteArray(64)))
        assertEquals("me@example.com", email)
        assertTrue(calls[0].contains("\"scope\":\"profile\""))
        assertTrue(calls[1].startsWith("GET https://profile.accounts.firefox.com/v1/profile Bearer tok"))
    }
}
