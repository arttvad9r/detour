package dev.triplet.app.data

import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesMappingTest {

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

    @Test fun `apps sorted by label case-insensitive`() {
        val sorted = RoutesMapping.sortApps(listOf(AppInfo("b", "Zebra", isSystem = false), AppInfo("a", "apple", isSystem = false)))
        assertEquals(listOf("apple", "Zebra"), sorted.map { it.label })
    }
}
