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

    @Test fun `delete inactive preserves active`() {
        val keys = VlessKeys(listOf(VlessKey("a", "a", "a"), VlessKey("b", "b", "b")), "b").delete("a")
        assertEquals("b", keys.activeId)
    }

    @Test fun `delete active chooses remaining and deleting only clears active`() {
        assertEquals("b", VlessKeys(listOf(VlessKey("a", "a", "a"), VlessKey("b", "b", "b")), "a").delete("a").activeId)
        assertEquals(null, VlessKeys(listOf(VlessKey("a", "a", "a")), "a").delete("a").activeId)
    }

    @Test fun `delete nonexistent is unchanged`() {
        val keys = VlessKeys(listOf(VlessKey("a", "a", "a")), "a")
        assertEquals(keys, keys.delete("missing"))
    }
}
