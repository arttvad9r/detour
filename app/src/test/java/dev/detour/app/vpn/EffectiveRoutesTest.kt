package dev.detour.app.vpn

import dev.detour.app.core.AppRoute
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

    @Test fun `excluded host uid is never routed`() {
        val result = effectiveRoutes(
            mapOf("self" to AppRoute.VPN, "other" to AppRoute.DPI),
            mapOf("self" to 10000, "other" to 10001),
            mapOf(10000 to setOf("self"), 10001 to setOf("other")),
            excludedUids = setOf(10000),
        )

        assertTrue("self" !in result.packages)
        assertEquals(setOf("other"), result.dpiPackages)
    }

    @Test fun `all packages sharing excluded host uid are skipped`() {
        val result = effectiveRoutes(
            mapOf("self" to AppRoute.VPN, "sibling" to AppRoute.VPN),
            mapOf("self" to 10000, "sibling" to 10000),
            mapOf(10000 to setOf("self", "sibling")),
            excludedUids = setOf(10000),
        )

        assertTrue(result.isEmpty)
        assertTrue(result.sharedUidConflict.isEmpty())
    }

    @Test fun `ordinary uid ownership remains trusted`() {
        assertEquals(
            setOf("selected"),
            trustworthyUidPackages(setOf("selected"), "selected"),
        )
    }

    @Test fun `shared uid with hidden siblings is rejected from official uid name`() {
        val ownership = trustworthyUidPackages(
            visiblePackages = setOf("selected"),
            officialUidName = "shared.example:10001",
        )
        val result = effectiveRoutes(
            mapOf("selected" to AppRoute.VPN),
            mapOf("selected" to 10001),
            mapOf(10001 to ownership),
        )

        assertEquals(setOf(10001), result.sharedUidConflict)
        assertTrue(result.isEmpty)
    }
}
