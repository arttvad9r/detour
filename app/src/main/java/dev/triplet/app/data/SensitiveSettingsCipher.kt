package dev.triplet.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Authenticated encryption for VPN credentials persisted in DataStore.
 *
 * The AES key never leaves Android Keystore. AAD binds each ciphertext to its
 * preference name so encrypted VLESS/WARP payloads cannot be swapped between
 * slots without authentication failing.
 */
internal class SensitiveSettingsCipher {
    fun encryptIfNeeded(slot: String, stored: String): String {
        if (stored.isBlank() || stored.startsWith(PREFIX)) return stored
        return encrypt(slot, stored)
    }

    fun encrypt(slot: String, plaintext: String): String {
        if (plaintext.isBlank()) return plaintext
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(slot.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return buildString {
            append(PREFIX)
            append(Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            append(':')
            append(Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }
    }

    /**
     * Returns plaintext for legacy values, decrypted text for v1 ciphertexts,
     * or null when authenticated decryption fails. Callers must fail closed on
     * null and must never persist the undecipherable value as plaintext.
     */
    fun decrypt(slot: String, stored: String): String? {
        if (stored.isBlank()) return ""
        if (!stored.startsWith(PREFIX)) return stored
        return runCatching {
            val payload = stored.removePrefix(PREFIX)
            val parts = payload.split(':', limit = 2)
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            require(iv.isNotEmpty() && ciphertext.isNotEmpty())

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(slot.toByteArray(StandardCharsets.UTF_8))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generator.generateKey()
        }
    }

    private companion object {
        const val PREFIX = "enc:v1:"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "dev.triplet.app.settings.aesgcm.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        val KEY_LOCK = Any()
    }
}
