package dev.detour.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardProfilesTest {
    private fun profile(id: String, name: String, version: Int? = null): WarpProfile = WarpProfile(
        id = id,
        name = name,
        proxies = listOf(
            WarpProxy(
                name = name,
                server = "203.0.113.10",
                port = 51820,
                ip = "10.0.0.2",
                privateKey = "private-key-$id",
                publicKey = "public-key-$id",
                reserved = emptyList(),
                allowedIps = listOf("0.0.0.0/0"),
                amnezia = AmneziaWgOptions(version = version),
            ),
        ),
    )

    @Test fun legacySingleProfileBecomesSelectedCollectionItem() {
        val legacy = profile("warp-1", "Cloudflare WARP")
        val parsed = WireGuardProfiles.fromStored(legacy.toJson())

        assertEquals(listOf(legacy), parsed.items)
        assertEquals(legacy.id, parsed.activeId)
        assertEquals(legacy, parsed.active)
    }

    @Test fun multipleProfilesRoundTripWithoutReplacingEachOther() {
        val warp = profile("warp-1", "Cloudflare WARP")
        val awg = profile("awg-1", "My VPS", version = 3)
        val original = WireGuardProfiles(listOf(warp, awg), awg.id)

        val restored = WireGuardProfiles.fromStored(original.toJson())

        assertEquals(original, restored)
        assertEquals(2, restored.items.size)
        assertEquals(awg, restored.active)
    }

    @Test fun deletingInactiveProfileKeepsSelectionAndDeletingActiveFailsClosed() {
        val warp = profile("warp-1", "Cloudflare WARP")
        val awg = profile("awg-1", "My VPS", version = 3)
        val profiles = WireGuardProfiles(listOf(warp, awg), awg.id)

        val withoutWarp = profiles.delete(warp.id)
        assertEquals(awg.id, withoutWarp.activeId)
        assertEquals(awg, withoutWarp.active)

        val withoutActive = profiles.delete(awg.id)
        assertNull(withoutActive.activeId)
        assertNull(withoutActive.active)
        assertTrue(withoutActive.items.contains(warp))
    }
}
