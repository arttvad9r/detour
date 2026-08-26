package dev.triplet.app.data

import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun `defaults when empty`() {
        val s = RoutesMapping.toSettings(emptyMap())
        assertEquals("", s.vlessUri)
        assertEquals(DpiPreset.RECOMMENDED, s.preset)
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

    @Test fun `corrupted keys fall back to legacy uri`() {
        val s = RoutesMapping.toSettings(mapOf("vless_keys" to "{bad", "vless_uri" to "legacy"))
        assertEquals("legacy", s.vlessUri)
    }
}
