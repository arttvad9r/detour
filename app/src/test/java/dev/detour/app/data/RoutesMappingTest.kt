package dev.detour.app.data

import dev.detour.app.core.AmneziaWgOptions
import dev.detour.app.core.AppRoute
import dev.detour.app.core.DpiPreset
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.core.WarpProfile
import dev.detour.app.core.WarpProxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesMappingTest {
    @Test
    fun `system apps visibility is restored from settings`() {
        assertTrue(RoutesMapping.toSettings(mapOf("show_system_apps" to true)).showSystemApps)
    }

    @Test fun `routes partition into vpn and dpi sets`() {
        val s = RoutesMapping.toSettings(
            mapOf(
                "vless_uri" to "vless://x",
                "dpi_preset" to "compatible",
                "route:a" to AppRoute.VPN.name,
                "route:b" to AppRoute.DPI.name,
                "route:c" to AppRoute.DIRECT.name,
            ),
        )
        assertEquals("vless://x", s.vlessUri)
        assertEquals(DpiPreset.RECOMMENDED, s.preset)
        // Легаси-id «compatible» мигрирует на единственную встроенную стратегию
        assertEquals(DpiPreset.RECOMMENDED, DpiPreset.byId("compatible"))
        assertEquals(AppRoute.VPN, s.routes["a"])
        assertEquals(AppRoute.DPI, s.routes["b"])
        // DIRECT не хранится как отдельная запись-маршрут
        assertFalse(s.routes.containsKey("c"))
    }

    @Test fun `WARP profile and selection are restored`() {
        val warp = WarpProfile(
            id = "warp", name = "WARP",
            proxies = listOf(
                WarpProxy(
                    name = "endpoint", server = "warp.example", port = 4500,
                    ip = "172.16.0.2", privateKey = "private", publicKey = "public",
                    reserved = listOf(1, 2, 3), allowedIps = listOf("0.0.0.0/0"),
                    amnezia = AmneziaWgOptions(jc = 4),
                ),
            ),
        )
        val s = RoutesMapping.toSettings(
            mapOf(
                "warp_profile" to warp.toJson(),
                "vpn_profile_kind" to "WARP",
            ),
        )
        assertEquals(VpnProfileKind.WARP, s.activeVpn)
        assertEquals(warp, s.warpProfile)
        assertTrue(s.activeVpnConfigured)
    }

    @Test fun `missing or corrupt WARP stays selected but unconfigured`() {
        val missing = RoutesMapping.toSettings(mapOf("vpn_profile_kind" to "WARP"))
        assertEquals(VpnProfileKind.WARP, missing.activeVpn)
        assertNull(missing.warpProfile)
        assertFalse(missing.activeVpnConfigured)

        val corrupt = RoutesMapping.toSettings(
            mapOf("vpn_profile_kind" to "WARP", "warp_profile" to "{broken"),
        )
        assertEquals(VpnProfileKind.WARP, corrupt.activeVpn)
        assertNull(corrupt.warpProfile)
        assertFalse(corrupt.activeVpnConfigured)
    }

    @Test fun `defaults when empty`() {
        val s = RoutesMapping.toSettings(emptyMap())
        assertEquals("", s.vlessUri)
        assertEquals(DpiPreset.RECOMMENDED, s.preset)
        assertEquals(VpnProfileKind.VLESS, s.activeVpn)
        assertTrue(s.routes.isEmpty())
    }

    @Test fun `unknown preset falls back to recommended`() {
        val s = RoutesMapping.toSettings(mapOf("dpi_preset" to "bogus"))
        assertEquals(DpiPreset.RECOMMENDED, s.preset)
    }

    @Test fun `unknown route is ignored`() {
        val s = RoutesMapping.toSettings(mapOf("route:a" to "FUTURE"))
        assertTrue(s.routes.isEmpty())
    }

    @Test fun `corrupted keys do not resurrect legacy uri`() {
        val s = RoutesMapping.toSettings(mapOf("vless_keys" to "{bad", "vless_uri" to "legacy"))
        assertEquals("", s.vlessUri)
    }
}
