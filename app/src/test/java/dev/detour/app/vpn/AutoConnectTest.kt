package dev.detour.app.vpn

import dev.detour.app.core.AmneziaWgOptions
import dev.detour.app.core.AppRoute
import dev.detour.app.core.DpiPreset
import dev.detour.app.core.VlessKey
import dev.detour.app.core.VlessKeys
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.core.WarpProfile
import dev.detour.app.core.WarpProxy
import dev.detour.app.data.TriSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConnectTest {
    private val validVlessUri =
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
            "?type=tcp&security=reality&fp=chrome&sni=translate.yandex.com" +
            "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
            "&flow=xtls-rprx-vision#MyServer"

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
        vlessKeys = if (key) VlessKeys(listOf(VlessKey("a", "a", validVlessUri)), "a") else VlessKeys(emptyList(), null),
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
