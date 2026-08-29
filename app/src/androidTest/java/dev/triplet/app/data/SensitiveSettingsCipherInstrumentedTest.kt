package dev.triplet.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensitiveSettingsCipherInstrumentedTest {
    private val cipher = SensitiveSettingsCipher()

    @Test fun roundTripKeepsPlaintextOutOfStoredValue() {
        val plaintext = "vless://credential-marker@example.invalid:443"
        val stored = cipher.encrypt("vless_keys", plaintext)

        assertTrue(stored.startsWith("enc:v1:"))
        assertFalse(stored.contains(plaintext))
        assertEquals(plaintext, cipher.decrypt("vless_keys", stored))
    }

    @Test fun ciphertextIsBoundToPreferenceSlot() {
        val stored = cipher.encrypt("vless_keys", "secret")

        assertNull(cipher.decrypt("warp_profile", stored))
    }

    @Test fun tamperedCiphertextIsRejected() {
        val stored = cipher.encrypt("warp_profile", "private-key-marker")
        val replacement = if (stored.last() == 'A') 'B' else 'A'
        val tampered = stored.dropLast(1) + replacement

        assertNull(cipher.decrypt("warp_profile", tampered))
    }

    @Test fun plaintextIsAcceptedOnlyByMigrationCompatibilityPath() {
        val legacy = "vless://legacy-marker@example.invalid:443"

        assertNull(cipher.decrypt("vless_keys", legacy))
        assertEquals(legacy, cipher.decryptLegacyCompatible("vless_keys", legacy))
    }
}
