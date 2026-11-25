package app.zen.android.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SyncCryptoTest {

    private val bundle = KeyBundle(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 100).toByte() })

    @Test
    fun `key bundle round-trips a payload`() {
        val payload = bundle.encrypt("""{"id":"abc","kind":"space"}""")
        assertEquals("""{"id":"abc","kind":"space"}""", bundle.decrypt(payload))
    }

    @Test(expected = SyncCryptoException::class)
    fun `tampered ciphertext is rejected by the HMAC check`() {
        val payload = JSONObject(bundle.encrypt("hello"))
        val tampered = payload.put("ciphertext", B64.std(ByteArray(16)))
        bundle.decrypt(tampered.toString())
    }

    @Test
    fun `scoped key is split into encryption and HMAC halves`() {
        val k = ByteArray(64) { it.toByte() }
        val split = KeyBundle.fromScopedKey(k)
        assertEquals(0, split.encKey[0].toInt())
        assertEquals(32, split.hmacKey[0].toInt())
    }

    @Test
    fun `pkce challenge is the base64url SHA-256 of the verifier`() {
        val pkce = Pkce("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkce.challenge)
    }

    @Test
    fun `decrypts an ECDH-ES A256GCM JWE addressed to its public JWK`() {
        val receiver = ScopedKeysExchange()
        val jwk = JSONObject(receiver.publicJwk)
        val receiverPub = ScopedKeysExchange.publicKeyFrom(B64.fromUrl(jwk.getString("x")), B64.fromUrl(jwk.getString("y")))

        val ephemeral = ScopedKeysExchange.generate()
        val epkPoint = (ephemeral.public as java.security.interfaces.ECPublicKey).w
        fun fixed(b: java.math.BigInteger) = b.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else ByteArray(32 - it.size) + it }
        val header = JSONObject()
            .put("alg", "ECDH-ES").put("enc", "A256GCM")
            .put("epk", JSONObject().put("kty", "EC").put("crv", "P-256")
                .put("x", B64.url(fixed(epkPoint.affineX))).put("y", B64.url(fixed(epkPoint.affineY))))
        val headerB64 = B64.url(header.toString().toByteArray())
        val z = KeyAgreement.getInstance("ECDH").run { init(ephemeral.private); doPhase(receiverPub, true); generateSecret() }
        val cek = ScopedKeysExchange.concatKdf(z, "A256GCM", 256)
        val iv = randomBytes(12)
        val sealed = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, iv))
            updateAAD(headerB64.toByteArray())
            doFinal("""{"secret":42}""".toByteArray())
        }
        val ct = sealed.copyOfRange(0, sealed.size - 16)
        val tag = sealed.copyOfRange(sealed.size - 16, sealed.size)
        val jwe = listOf(headerB64, "", B64.url(iv), B64.url(ct), B64.url(tag)).joinToString(".")

        assertEquals("""{"secret":42}""", receiver.decrypt(jwe))
    }

    @Test
    fun `concat KDF matches the RFC 7518 appendix C derivation shape`() {
        val z = ByteArray(32) { 1 }
        val expected = sha256(
            ByteBuffer.allocate(4 + 32 + 4 + 7 + 12).putInt(1).put(z).putInt(7).put("A256GCM".toByteArray())
                .putInt(0).putInt(0).putInt(256).array()
        )
        assertTrue(expected.contentEquals(ScopedKeysExchange.concatKdf(z, "A256GCM", 256)))
    }

    @Test
    fun `hawk header carries id, timestamp, nonce and a verifiable mac`() {
        val url = java.net.URL("https://sync.example.com/1.5/123/storage/spaces?full=1")
        val header = Hawk.header("my-id", "my-key", "GET", url, 1_700_000_000, "abc")
        val normalized = "hawk.1.header\n1700000000\nabc\nGET\n/1.5/123/storage/spaces?full=1\nsync.example.com\n443\n\n\n"
        val mac = B64.std(hmacSha256("my-key".toByteArray(), normalized.toByteArray()))
        assertEquals("""Hawk id="my-id", ts="1700000000", nonce="abc", mac="$mac"""", header)
    }
}
