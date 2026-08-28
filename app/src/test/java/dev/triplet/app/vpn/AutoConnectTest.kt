package dev.triplet.app.vpn

import dev.triplet.app.core.AmneziaWgOptions
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.core.WarpProfile
import dev.triplet.app.core.WarpProxy
import dev.triplet.app.data.TriSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConnectTest {
    private val warp = WarpProfile(
        id = "warp",
        name = "WARP",
        proxies = listOf(
            WarpProxy(
                name = "endpoint", server = "warp.example", port = 4500,
                ip = "172.16.0.2", privateKey = "private", publicKey = "public",
                reserved = listOf(1, 2, 3), allowedIps = listOf("0.0.0.0/0"),
                amnezia = AmneziaWgOptions(jc = 4),
            ),
        ),
    )

    private fun settings(
        route: AppRoute,
        key: Boolean = false,
        activeVpn: VpnProfileKind = VpnProfileKind.VLESS,
        warpProfile: WarpProfile? = null,
    ) = TriSettings(
        vlessKeys = if (key) VlessKeys(listOf(VlessKey("a", "a", "uri")), "a") else VlessKeys(emptyList(), null),
        warpProfile = warpProfile,
        activeVpn = activeVpn,
        preset = DpiPreset.RECOMMENDED, dpiCustomArgs = "", autoConnect = true,
        themeId = "lavenda", dnsId = "google", dnsCustom = "", routes = mapOf("app" to route),
        showSystemApps = false, sessionStartedAt = null,
    )

    @Test fun `vpn needs selected profile and permission`() {
        val effective = EffectiveRoutes(setOf("app"), emptySet())
        assertFalse(canAutoConnect(settings(AppRoute.VPN), true, effective))
        assertTrue(canAutoConnect(settings(AppRoute.VPN, key = true), true, effective))
        assertFalse(canAutoConnect(settings(AppRoute.VPN, key = true), false, effective))
        assertTrue(canAutoConnect(
            settings(AppRoute.VPN, activeVpn = VpnProfileKind.WARP, warpProfile = warp),
            true,
            effective,
        ))
    }

    @Test fun `dpi only needs no vpn profile`() {
        assertTrue(canAutoConnect(settings(AppRoute.DPI), true, EffectiveRoutes(emptySet(), setOf("app"))))
    }

    @Test fun `no routes does not connect`() {
        assertFalse(canAutoConnect(settings(AppRoute.DIRECT), true, EffectiveRoutes(emptySet(), emptySet())))
    }

    @Test fun `removed vpn route does not require a profile for dpi`() {
        assertTrue(canAutoConnect(
            settings(AppRoute.VPN), true,
            EffectiveRoutes(vpnPackages = emptySet(), dpiPackages = setOf("dpi-app")),
            activeVpnValid = false,
        ))
    }
}
