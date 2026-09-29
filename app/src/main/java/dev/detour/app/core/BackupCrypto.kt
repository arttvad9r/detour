package dev.detour.app.core

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * Optional password protection for backup documents. The envelope is a small
 * JSON object (`v` = 5) holding AES-256-GCM ciphertext of the plain
 * backup JSON; the key comes from PBKDF2-HMAC-SHA256. The header fields are
 * authenticated as AAD so they cannot be swapped without detection.
 */
object BackupCrypto {
    const val ENVELOPE_VERSION = 5
    const val ITERATIONS = 600_000
    const val MAX_ITERATIONS = 2_000_000
    private const val APP = "detour"
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    sealed interface Decrypted {
        data class Plain(val json: String) : Decrypted
        data object WrongPassword : Decrypted
        data object Invalid : Decrypted
    }

    fun isEncrypted(document: String): Boolean = try {
        val o = JSONObject(document)
        o.optString("app") == APP && o.optInt("v") == ENVELOPE_VERSION && o.has("ct")
    } catch (_: Exception) { false }

    fun encrypt(plainJson: String, password: CharArray, random: SecureRandom = SecureRandom()): String {
        require(password.isNotEmpty()) { "password must not be empty" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad(ITERATIONS))
        val ct = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return JSONObject().apply {
            put("app", APP)
            put("v", ENVELOPE_VERSION)
            put("kdf", KDF)
            put("iters", ITERATIONS)
            put("salt", b64.encodeToString(salt))
            put("iv", b64.encodeToString(iv))
            put("ct", b64.encodeToString(ct))
        }.toString(2)
    }

    fun decrypt(document: String, password: CharArray): Decrypted = try {
        val o = JSONObject(document)
        val iter = o.getInt("iters")
        if (o.optString("app") != APP || o.optInt("v") != ENVELOPE_VERSION ||
            o.optString("kdf") != KDF || iter !in 1..MAX_ITERATIONS || password.isEmpty()
        ) {
            Decrypted.Invalid
        } else {
            val b64 = Base64.getDecoder()
            val salt = b64.decode(o.getString("salt"))
            val iv = b64.decode(o.getString("iv"))
            val ct = b64.decode(o.getString("ct"))
            if (salt.size != SALT_BYTES || iv.size != IV_BYTES) {
                Decrypted.Invalid
            } else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iter), GCMParameterSpec(TAG_BITS, iv))
                cipher.updateAAD(aad(iter))
                try {
                    Decrypted.Plain(String(cipher.doFinal(ct), Charsets.UTF_8))
                } catch (_: AEADBadTagException) {
                    Decrypted.WrongPassword
                }
            }
        }
    } catch (_: Exception) {
        Decrypted.Invalid
    }

    private fun aad(iter: Int): ByteArray =
        "$APP|$ENVELOPE_VERSION|$KDF|$iter".toByteArray(Charsets.UTF_8)

    private fun key(password: CharArray, salt: ByteArray, iter: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iter, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
