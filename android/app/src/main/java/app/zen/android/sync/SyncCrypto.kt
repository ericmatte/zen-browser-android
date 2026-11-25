package app.zen.android.sync

import org.json.JSONObject
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class SyncCryptoException(message: String) : Exception(message)

object B64 {
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDecoder = Base64.getUrlDecoder()

    fun url(bytes: ByteArray): String = urlEncoder.encodeToString(bytes)
    fun fromUrl(s: String): ByteArray = urlDecoder.decode(s.trimEnd('='))
    fun std(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun fromStd(s: String): ByteArray = Base64.getDecoder().decode(s)
}

private val random = SecureRandom()

fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** A Sync 1.5 key bundle: 32 bytes AES-256 key + 32 bytes HMAC-SHA256 key. */
class KeyBundle(val encKey: ByteArray, val hmacKey: ByteArray) {
    init {
        require(encKey.size == 32 && hmacKey.size == 32) { "Sync keys must be 32 bytes" }
    }

    fun decrypt(payload: String): String {
        val json = JSONObject(payload)
        val ciphertextB64 = json.getString("ciphertext")
        val expected = hmacSha256(hmacKey, ciphertextB64.toByteArray(Charsets.US_ASCII)).toHex()
        if (!MessageDigest.isEqual(expected.toByteArray(), json.getString("hmac").toByteArray())) {
            throw SyncCryptoException("HMAC mismatch")
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(B64.fromStd(json.getString("IV"))))
        return String(cipher.doFinal(B64.fromStd(ciphertextB64)), Charsets.UTF_8)
    }

    fun encrypt(cleartext: String, iv: ByteArray = randomBytes(16)): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
        val ciphertextB64 = B64.std(cipher.doFinal(cleartext.toByteArray(Charsets.UTF_8)))
        val hmac = hmacSha256(hmacKey, ciphertextB64.toByteArray(Charsets.US_ASCII)).toHex()
        return JSONObject()
            .put("ciphertext", ciphertextB64)
            .put("IV", B64.std(iv))
            .put("hmac", hmac)
            .toString()
    }

    companion object {
        fun fromScopedKey(k: ByteArray): KeyBundle {
            if (k.size != 64) throw SyncCryptoException("oldsync key must be 64 bytes, got ${k.size}")
            return KeyBundle(k.copyOfRange(0, 32), k.copyOfRange(32, 64))
        }
    }
}

class Pkce(val verifier: String = B64.url(randomBytes(32))) {
    val challenge: String = B64.url(sha256(verifier.toByteArray(Charsets.US_ASCII)))
}

/**
 * Ephemeral P-256 key pair used for the FxA `keys_jwk` / `keys_jwe` exchange
 * (JWE, alg=ECDH-ES, enc=A256GCM).
 */
class ScopedKeysExchange(private val keyPair: KeyPair = generate()) {

    val publicJwk: String
        get() {
            val point = (keyPair.public as ECPublicKey).w
            return JSONObject()
                .put("kty", "EC")
                .put("crv", "P-256")
                .put("x", B64.url(point.affineX.toFixed32()))
                .put("y", B64.url(point.affineY.toFixed32()))
                .toString()
        }

    fun decrypt(jwe: String): String {
        val parts = jwe.split('.')
        if (parts.size != 5) throw SyncCryptoException("Malformed JWE")
        val header = JSONObject(String(B64.fromUrl(parts[0]), Charsets.UTF_8))
        if (header.getString("alg") != "ECDH-ES" || header.getString("enc") != "A256GCM") {
            throw SyncCryptoException("Unsupported JWE alg/enc")
        }
        val epk = header.getJSONObject("epk")
        val peer = publicKeyFrom(B64.fromUrl(epk.getString("x")), B64.fromUrl(epk.getString("y")))
        val z = KeyAgreement.getInstance("ECDH").run {
            init(keyPair.private)
            doPhase(peer, true)
            generateSecret()
        }
        val cek = concatKdf(z, "A256GCM", 256)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, B64.fromUrl(parts[2])))
        cipher.updateAAD(parts[0].toByteArray(Charsets.US_ASCII))
        return String(cipher.doFinal(B64.fromUrl(parts[3]) + B64.fromUrl(parts[4])), Charsets.UTF_8)
    }

    companion object {
        private fun ecSpec() = java.security.AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        }

        fun generate(): KeyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }

        fun publicKeyFrom(x: ByteArray, y: ByteArray): ECPublicKey =
            KeyFactory.getInstance("EC").generatePublic(
                ECPublicKeySpec(ECPoint(BigInteger(1, x), BigInteger(1, y)), ecSpec())
            ) as ECPublicKey

        /** NIST SP 800-56A Concat KDF as profiled by RFC 7518 §4.6, with empty apu/apv. */
        fun concatKdf(z: ByteArray, algorithmId: String, keyBits: Int): ByteArray {
            val alg = algorithmId.toByteArray(Charsets.US_ASCII)
            val buf = ByteBuffer.allocate(4 + z.size + 4 + alg.size + 4 + 4 + 4)
                .putInt(1).put(z)
                .putInt(alg.size).put(alg)
                .putInt(0).putInt(0)
                .putInt(keyBits)
            return sha256(buf.array()).copyOf(keyBits / 8)
        }

        private fun BigInteger.toFixed32(): ByteArray {
            val raw = toByteArray()
            return when {
                raw.size == 32 -> raw
                raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
                else -> ByteArray(32 - raw.size) + raw
            }
        }
    }
}

object Hawk {
    fun header(
        id: String,
        key: String,
        method: String,
        url: java.net.URL,
        timestampSeconds: Long = System.currentTimeMillis() / 1000,
        nonce: String = B64.url(randomBytes(8)),
    ): String {
        val port = if (url.port != -1) url.port else url.defaultPort
        val resource = url.path + (url.query?.let { "?$it" } ?: "")
        val normalized = buildString {
            append("hawk.1.header\n")
            append("$timestampSeconds\n")
            append("$nonce\n")
            append("${method.uppercase()}\n")
            append("$resource\n")
            append("${url.host.lowercase()}\n")
            append("$port\n")
            append("\n")
            append("\n")
        }
        val mac = B64.std(hmacSha256(key.toByteArray(Charsets.UTF_8), normalized.toByteArray(Charsets.UTF_8)))
        return """Hawk id="$id", ts="$timestampSeconds", nonce="$nonce", mac="$mac""""
    }
}
