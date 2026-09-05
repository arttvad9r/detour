package dev.triplet.app.vpn

import dev.triplet.app.core.AmneziaWgOptions
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DestinationRule
import dev.triplet.app.core.DestinationRuleType
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.MultiHopEntryRef
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.core.WarpProfile
import dev.triplet.app.core.WarpProxy
import dev.triplet.app.data.TriSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    @Test fun `vpn destination override also requires selected profile`() {
        val destinationVpn = DestinationRule(
            DestinationRuleType.DOMAIN,
            "example.com",
            AppRoute.VPN,
        )
        val effective = EffectiveRoutes(emptySet(), setOf("app"))
        assertFalse(
            canAutoConnect(
                settings(AppRoute.DPI).copy(destinationRules = listOf(destinationVpn)),
                true,
                effective,
            ),
        )
        assertTrue(
            canAutoConnect(
                settings(AppRoute.DPI, key = true).copy(destinationRules = listOf(destinationVpn)),
                true,
                effective,
            ),
        )
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

    @Test fun `network triggers use independent preferences`() {
        val networkSettings = settings(AppRoute.DPI).copy(
            autoConnect = false,
            autoConnectWifi = true,
            autoConnectCellular = false,
        )
        assertFalse(isAutoConnectEnabled(networkSettings, AutoConnectTrigger.APP_LAUNCH))
        assertTrue(isAutoConnectEnabled(networkSettings, AutoConnectTrigger.WIFI))
        assertFalse(isAutoConnectEnabled(networkSettings, AutoConnectTrigger.CELLULAR))

        val effective = EffectiveRoutes(emptySet(), setOf("app"))
        assertTrue(
            canAutoConnect(
                networkSettings,
                vpnPermissionGranted = true,
                effective = effective,
                enabled = isAutoConnectEnabled(networkSettings, AutoConnectTrigger.WIFI),
            ),
        )
        assertFalse(
            canAutoConnect(
                networkSettings,
                vpnPermissionGranted = true,
                effective = effective,
                enabled = isAutoConnectEnabled(networkSettings, AutoConnectTrigger.CELLULAR),
            ),
        )
    }

    @Test fun `coordinator honors trigger-specific setting`() = runBlocking {
        val networkSettings = settings(AppRoute.DPI).copy(
            autoConnect = false,
            autoConnectWifi = true,
            autoConnectCellular = false,
        )
        var starts = 0

        suspend fun run(trigger: AutoConnectTrigger): Boolean = AutoConnectCoordinator(
            loadSettings = { networkSettings },
            resolveRoutes = { EffectiveRoutes(emptySet(), setOf("app")) },
            vpnPermissionGranted = { true },
            currentVpnState = { VpnState.Idle },
            startVpn = { starts += 1 },
            trigger = trigger,
        ).runOnce()

        assertFalse(run(AutoConnectTrigger.APP_LAUNCH))
        assertTrue(run(AutoConnectTrigger.WIFI))
        assertFalse(run(AutoConnectTrigger.CELLULAR))
        assertEquals(1, starts)
    }

    @Test fun `auto-connect preflight validates multi-hop with service resolver semantics`() {
        val exit = VlessKey("a", "Exit", validVlessUri)
        val entry = VlessKey(
            "b",
            "Entry",
            validVlessUri.replace("example.com", "entry.example.com").replace("#MyServer", "#Entry"),
        )
        val base = settings(AppRoute.VPN, key = true).copy(
            vlessKeys = VlessKeys(listOf(exit, entry), "a"),
        )

        assertTrue(autoConnectProfileValid(base.copy(multiHopEntry = MultiHopEntryRef.Vless("b"))))
        assertFalse(autoConnectProfileValid(base.copy(multiHopEntry = MultiHopEntryRef.Vless("missing"))))
        assertFalse(autoConnectProfileValid(base.copy(multiHopEntry = MultiHopEntryRef.Vless("a"))))

        val subscription = VlessKey(
            id = "subscription",
            name = "Subscription",
            uri = "https://subscription.example/token",
        )
        assertFalse(
            autoConnectProfileValid(
                base.copy(
                    vlessKeys = base.vlessKeys.copy(items = base.vlessKeys.items + subscription),
                    multiHopEntry = MultiHopEntryRef.Vless("subscription"),
                ),
            ),
        )
        assertFalse(autoConnectProfileValid(base.copy(multiHopEntry = MultiHopEntryRef.Warp)))
    }
}
