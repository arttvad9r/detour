package dev.detour.app.ui

import dev.detour.app.core.AppRoute
import dev.detour.app.core.VlessKey
import dev.detour.app.core.VlessKeys
import dev.detour.app.core.DpiPreset
import dev.detour.app.data.RoutesMapping
import dev.detour.app.vpn.EffectiveRoutes
import dev.detour.app.vpn.ResolvedRouteSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteReadinessTest {
    private val pkg = "example.video"
    private val uid = 10123
    private val fresh = RoutesMapping.toSettings(emptyMap())
    private val dpi = fresh.copy(routes = mapOf(pkg to AppRoute.DPI))
    private val snapshot = ResolvedRouteSnapshot(EffectiveRoutes(emptySet(), setOf(pkg)), mapOf(pkg to uid))

    @Test fun `direct does not depend on other app conflicts or VPN profiles`() {
        val conflict = snapshot.copy(effective = EffectiveRoutes(emptySet(), emptySet(), setOf(1001)))
        assertEquals(RouteReadiness.DIRECT, routeReadiness(pkg, fresh, conflict, true, 10100))
    }

    @Test fun `removed app cannot look configured`() {
        assertEquals(RouteReadiness.REMOVED, routeReadiness(pkg, dpi, snapshot, false, 10100))
    }

    @Test fun `own process cannot be routed back into itself`() {
        assertEquals(RouteReadiness.EXCLUDED, routeReadiness(pkg, dpi, snapshot, true, uid))
    }

    @Test fun `shared UID rejection distinguishes this group from other conflicts`() {
        for ((conflicting, expected) in listOf(uid to RouteReadiness.SHARED_UID, 1001 to RouteReadiness.OTHER_CONFLICT)) {
            val conflict = snapshot.copy(effective = EffectiveRoutes(emptySet(), emptySet(), setOf(conflicting)))
            assertEquals(expected, routeReadiness(pkg, dpi, conflict, true, 10100))
        }
    }

    @Test fun `DPI also depends on a VPN profile when another app needs VPN`() {
        val mixed = snapshot.copy(effective = EffectiveRoutes(setOf("example.browser"), setOf(pkg)))
        assertEquals(RouteReadiness.MISSING_PROFILE, routeReadiness(pkg, dpi, mixed, true, 10100))
    }

    @Test fun `invalid custom DPI settings do not look ready`() {
        val invalid = dpi.copy(preset = DpiPreset.CUSTOM, dpiCustomArgs = "")
        assertEquals(RouteReadiness.INVALID_DPI, routeReadiness(pkg, invalid, snapshot, true, 10100))
    }

    @Test fun `effective DPI assignment needs no VPN profile`() {
        assertEquals(RouteReadiness.CONFIGURED, routeReadiness(pkg, dpi, snapshot, true, 10100))
    }

    @Test fun `a saved assignment outside the effective set is never ready`() {
        val excluded = snapshot.copy(effective = EffectiveRoutes(emptySet(), emptySet()))
        assertEquals(RouteReadiness.UNAVAILABLE, routeReadiness(pkg, dpi, excluded, true, 10100))
    }

    @Test fun `an explicitly selected compatible VPN profile makes its assignment valid`() {
        val uri = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
            "?type=tcp&security=reality&fp=chrome&sni=example.com" +
            "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179"
        val configured = fresh.copy(routes = mapOf(pkg to AppRoute.VPN),
            vlessKeys = VlessKeys(listOf(VlessKey("test", "Synthetic VPN", uri)), "test"))
        val vpnSnapshot = snapshot.copy(effective = EffectiveRoutes(setOf(pkg), emptySet()))
        assertEquals(RouteReadiness.CONFIGURED, routeReadiness(pkg, configured, vpnSnapshot, true, 10100))
        val mixed = vpnSnapshot.copy(effective = EffectiveRoutes(setOf(pkg), setOf("example.other")))
        assertEquals(RouteReadiness.INVALID_DPI, routeReadiness(pkg,
            configured.copy(preset = DpiPreset.CUSTOM, dpiCustomArgs = "--listen 0.0.0.0"), mixed, true, 10100))
        val unselected = configured.copy(vlessKeys = VlessKeys(configured.vlessKeys.items, null))
        assertEquals(RouteReadiness.MISSING_PROFILE, routeReadiness(pkg, unselected, vpnSnapshot, true, 10100))
    }

}
