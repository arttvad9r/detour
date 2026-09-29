package dev.detour.app.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val plain = """{"v":4,"app":"detour","theme":"dracula","note":"Детур"}"""
    private val pw = "correct horse".toCharArray()

    @Test fun `round trip restores plain backup`() {
        val enc = BackupCrypto.encrypt(plain, pw)

        assertEquals(BackupCrypto.Decrypted.Plain(plain), BackupCrypto.decrypt(enc, pw))
    }

    @Test fun `envelope hides plaintext and is detected`() {
        val enc = BackupCrypto.encrypt(plain, pw)

        assertTrue(BackupCrypto.isEncrypted(enc))
        assertFalse(BackupCrypto.isEncrypted(plain))
        assertFalse(enc.contains("dracula"))
    }

    @Test fun `wrong password is reported distinctly`() {
        val enc = BackupCrypto.encrypt(plain, pw)

        assertEquals(BackupCrypto.Decrypted.WrongPassword, BackupCrypto.decrypt(enc, "nope".toCharArray()))
    }

    @Test fun `each export uses fresh salt and iv`() {
        val a = JSONObject(BackupCrypto.encrypt(plain, pw))
        val b = JSONObject(BackupCrypto.encrypt(plain, pw))

        assertNotEquals(a.getString("salt"), b.getString("salt"))
        assertNotEquals(a.getString("iv"), b.getString("iv"))
        assertNotEquals(a.getString("ct"), b.getString("ct"))
    }

    @Test fun `tampered ciphertext fails authentication`() {
        val o = JSONObject(BackupCrypto.encrypt(plain, pw))
        val ct = java.util.Base64.getDecoder().decode(o.getString("ct"))
        ct[0] = (ct[0].toInt() xor 1).toByte()
        o.put("ct", java.util.Base64.getEncoder().encodeToString(ct))

        assertEquals(BackupCrypto.Decrypted.WrongPassword, BackupCrypto.decrypt(o.toString(), pw))
    }

    @Test fun `tampered iteration count fails authentication`() {
        val o = JSONObject(BackupCrypto.encrypt(plain, pw))
        o.put("iter", BackupCrypto.ITERATIONS - 1)

        assertEquals(BackupCrypto.Decrypted.WrongPassword, BackupCrypto.decrypt(o.toString(), pw))
    }

    @Test fun `excessive iteration count is rejected without deriving`() {
        val o = JSONObject(BackupCrypto.encrypt(plain, pw))
        o.put("iter", BackupCrypto.MAX_ITERATIONS + 1)

        assertEquals(BackupCrypto.Decrypted.Invalid, BackupCrypto.decrypt(o.toString(), pw))
    }

    @Test fun `malformed documents are invalid`() {
        assertEquals(BackupCrypto.Decrypted.Invalid, BackupCrypto.decrypt("not json", pw))
        assertEquals(BackupCrypto.Decrypted.Invalid, BackupCrypto.decrypt(plain, pw))
        assertEquals(BackupCrypto.Decrypted.Invalid, BackupCrypto.decrypt("""{"app":"detour","enc":2,"iter":1}""", pw))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty password is refused on export`() {
        BackupCrypto.encrypt(plain, CharArray(0))
    }
}
