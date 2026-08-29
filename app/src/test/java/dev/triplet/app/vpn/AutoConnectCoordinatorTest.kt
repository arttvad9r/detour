package dev.triplet.app.vpn

import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.TriSettings
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

    @Test fun `coordinator does not start without permission or from non-idle state`() = runBlocking {
        var starts = 0
        val source = settings(AppRoute.DPI)
        val effective = EffectiveRoutes(vpnPackages = emptySet(), dpiPackages = setOf("app"))

        val noPermission = AutoConnectCoordinator(
            loadSettings = { source },
            resolveRoutes = { effective },
            vpnPermissionGranted = { false },
            currentVpnState = { VpnState.Idle },
            startVpn = { starts++ },
        )
        assertFalse(noPermission.runOnce())

        val alreadyActive = AutoConnectCoordinator(
            loadSettings = { source },
            resolveRoutes = { effective },
            vpnPermissionGranted = { true },
            currentVpnState = { VpnState.Active },
            startVpn = { starts++ },
        )
        assertFalse(alreadyActive.runOnce())
        assertEquals(0, starts)
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
