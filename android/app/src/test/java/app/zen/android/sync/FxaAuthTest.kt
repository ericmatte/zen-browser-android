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
}
