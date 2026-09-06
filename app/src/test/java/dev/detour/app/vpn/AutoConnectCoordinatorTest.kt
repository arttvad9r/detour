package dev.detour.app.vpn

import dev.detour.app.core.AppRoute
import dev.detour.app.core.DestinationRule
import dev.detour.app.core.DestinationRuleType
import dev.detour.app.core.DpiPreset
import dev.detour.app.core.VlessKeys
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.TriSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConnectCoordinatorTest {
    private fun settings(route: AppRoute, autoConnect: Boolean = true) = TriSettings(
        vlessKeys = VlessKeys(emptyList(), null),
        warpProfile = null,
        activeVpn = VpnProfileKind.VLESS,
        preset = DpiPreset.RECOMMENDED,
        dpiCustomArgs = "",
        autoConnect = autoConnect,
        themeId = "",
        dnsId = "google",
        dnsCustom = "",
        routes = mapOf("app" to route),
        showSystemApps = false,
        sessionStartedAt = null,
    )

    @Test fun `dpi only auto-connect starts once when permission is granted`() = runBlocking {
        var starts = 0
        val source = settings(AppRoute.DPI)
        val coordinator = AutoConnectCoordinator(
            loadSettings = { source },
            resolveRoutes = { routes ->
                assertEquals(source.routes, routes)
                EffectiveRoutes(vpnPackages = emptySet(), dpiPackages = setOf("app"))
            },
            vpnPermissionGranted = { true },
            currentVpnState = { VpnState.Idle },
            startVpn = { starts++ },
        )

        assertTrue(coordinator.runOnce())
        assertEquals(1, starts)
    }

    @Test fun `vpn destination override without profile does not auto-connect`() = runBlocking {
        var starts = 0
        val source = settings(AppRoute.DPI).copy(
            destinationRules = listOf(
                DestinationRule(DestinationRuleType.DOMAIN, "example.com", AppRoute.VPN),
            ),
        )
        val coordinator = AutoConnectCoordinator(
            loadSettings = { source },
            resolveRoutes = { EffectiveRoutes(vpnPackages = emptySet(), dpiPackages = setOf("app")) },
            vpnPermissionGranted = { true },
            currentVpnState = { VpnState.Idle },
            startVpn = { starts++ },
        )

        assertFalse(coordinator.runOnce())
        assertEquals(0, starts)
    }

    @Test fun `disabled auto-connect skips state permission and route resolution`() = runBlocking {
        var stateChecks = 0
        var permissionChecks = 0
        var routeResolutions = 0
        val coordinator = AutoConnectCoordinator(
            loadSettings = { settings(AppRoute.DPI, autoConnect = false) },
            resolveRoutes = {
                routeResolutions++
                EffectiveRoutes(emptySet(), setOf("app"))
            },
            vpnPermissionGranted = {
                permissionChecks++
                true
            },
            currentVpnState = {
                stateChecks++
                VpnState.Idle
            },
            startVpn = { error("must not start") },
        )

        assertFalse(coordinator.runOnce())
        assertEquals(0, stateChecks)
        assertEquals(0, permissionChecks)
        assertEquals(0, routeResolutions)
    }

    @Test fun `permission denial and non-idle state skip route resolution`() = runBlocking {
        var routeResolutions = 0
        var permissionChecks = 0
        val source = settings(AppRoute.DPI)

        val noPermission = AutoConnectCoordinator(
            loadSettings = { source },
            resolveRoutes = {
                routeResolutions++
                EffectiveRoutes(emptySet(), setOf("app"))
            },
            vpnPermissionGranted = {
                permissionChecks++
                false
            },
            currentVpnState = { VpnState.Idle },
            startVpn = { error("must not start") },
        )
        assertFalse(noPermission.runOnce())
        assertEquals(1, permissionChecks)
        assertEquals(0, routeResolutions)

        permissionChecks = 0
        val alreadyActive = AutoConnectCoordinator(
            loadSettings = { source },
            resolveRoutes = {
                routeResolutions++
                EffectiveRoutes(emptySet(), setOf("app"))
            },
            vpnPermissionGranted = {
                permissionChecks++
                true
            },
            currentVpnState = { VpnState.Active },
            startVpn = { error("must not start") },
        )
        assertFalse(alreadyActive.runOnce())
        assertEquals(0, permissionChecks)
        assertEquals(0, routeResolutions)
    }

    @Test fun `vpn route requires a valid selected profile`() = runBlocking {
        var starts = 0
        val source = settings(AppRoute.VPN)
        val coordinator = AutoConnectCoordinator(
            loadSettings = { source },
            resolveRoutes = { EffectiveRoutes(vpnPackages = setOf("app"), dpiPackages = emptySet()) },
            vpnPermissionGranted = { true },
            currentVpnState = { VpnState.Idle },
            startVpn = { starts++ },
        )

        assertFalse(coordinator.runOnce())
        assertEquals(0, starts)
    }
}
