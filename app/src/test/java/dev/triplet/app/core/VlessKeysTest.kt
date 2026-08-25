package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessKeysTest {
    @Test
    fun `legacy uri migrates to one active key`() {
        val keys = VlessKeys.fromStored("", "vless://legacy")

        assertEquals(1, keys.items.size)
        assertEquals("vless://legacy", keys.items.single().uri)
        assertEquals(keys.items.single().id, keys.activeId)
    }

    @Test
    fun `json round trip preserves keys and active selection`() {
        val original = VlessKeys(
            listOf(VlessKey("a", "Primary", "vless://a"), VlessKey("b", "Backup", "vless://b")),
            "b",
        )

        assertEquals(original, VlessKeys.fromJson(original.toJson()))
    }

    @Test
    fun `empty storage has no keys`() {
        assertTrue(VlessKeys.fromStored("", "").items.isEmpty())
    }
}
