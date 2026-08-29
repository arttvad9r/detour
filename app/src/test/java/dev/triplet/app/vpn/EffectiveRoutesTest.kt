package dev.triplet.app.vpn

import dev.triplet.app.core.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectiveRoutesTest {
    @Test fun `empty routes never become capture all`() {
        assertTrue(effectiveRoutes(emptyMap(), emptyMap()).isEmpty)
    }

    @Test fun `filters removed packages`() {
        val result = effectiveRoutes(mapOf("gone" to AppRoute.VPN), mapOf("gone" to null))
        assertTrue(result.isEmpty)
    }

    @Test fun `keeps vpn and dpi packages`() {
        val result = effectiveRoutes(
            mapOf("vpn" to AppRoute.VPN, "dpi" to AppRoute.DPI),
            mapOf("vpn" to 10001, "dpi" to 10002),
        )
        assertEquals(setOf("vpn"), result.vpnPackages)
        assertEquals(setOf("dpi"), result.dpiPackages)
    }

    @Test fun `shared uid conflict is rejected`() {
        val result = effectiveRoutes(
            mapOf("a" to AppRoute.VPN, "b" to AppRoute.DPI),
            mapOf("a" to 10001, "b" to 10001),
        )
        assertEquals(setOf(10001), result.sharedUidConflict)
        assertTrue(result.isEmpty)
    }

    @Test fun `unknown routed package is filtered before capture`() {
        val result = effectiveRoutes(
            mapOf("unknown" to AppRoute.VPN),
            mapOf("unknown" to null),
        )

        assertTrue(result.isEmpty)
    }

    @Test fun `unselected shared uid sibling is rejected`() {
        val result = effectiveRoutes(
            mapOf("selected" to AppRoute.VPN),
            mapOf("selected" to 10001),
            mapOf(10001 to setOf("selected", "sibling")),
        )

        assertEquals(setOf(10001), result.sharedUidConflict)
        assertTrue(result.isEmpty)
    }

    @Test fun `missing uid ownership is rejected when ownership map is supplied`() {
        val result = effectiveRoutes(
            mapOf("selected" to AppRoute.VPN),
            mapOf("selected" to 10001),
            emptyMap(),
        )

        assertEquals(setOf(10001), result.sharedUidConflict)
        assertTrue(result.isEmpty)
    }
}
