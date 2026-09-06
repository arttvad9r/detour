package dev.detour.app.vpn

import dev.detour.app.core.DpiPreset
import dev.detour.app.core.VlessKey
import dev.detour.app.core.VlessKeys
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.TriSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionAutoConnectTest {
    private val subscription = VlessKey(
        id = "subscription",
        name = "Subscription",
        uri = "https://subscription.example/opaque-token",
    )

    private fun settings(kind: VpnProfileKind) = TriSettings(
        vlessKeys = VlessKeys(listOf(subscription), subscription.id),
        warpProfile = null,
        activeVpn = kind,
        preset = DpiPreset.RECOMMENDED,
        dpiCustomArgs = "",
        autoConnect = true,
        themeId = "",
        dnsId = "google",
        dnsCustom = "",
        routes = emptyMap(),
        showSystemApps = false,
        sessionStartedAt = null,
    )

    @Test fun `subscription is valid only when selected as subscription`() {
        assertTrue(autoConnectProfileValid(settings(VpnProfileKind.SUBSCRIPTION)))
        assertFalse(autoConnectProfileValid(settings(VpnProfileKind.VLESS)))
    }
}
