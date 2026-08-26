package dev.triplet.app.vpn

import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.data.TriSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConnectTest {
    private fun settings(route: AppRoute, key: Boolean = false) = TriSettings(
        vlessKeys = if (key) VlessKeys(listOf(VlessKey("a", "a", "uri")), "a") else VlessKeys(emptyList(), null),
        preset = DpiPreset.RECOMMENDED, dpiCustomArgs = "", autoConnect = true,
        themeId = "lavenda", dnsId = "google", dnsCustom = "", routes = mapOf("app" to route),
        showSystemApps = false, sessionStartedAt = null,
    )

    @Test fun `vpn needs key and permission`() {
        assertFalse(canAutoConnect(settings(AppRoute.VPN), true))
        assertTrue(canAutoConnect(settings(AppRoute.VPN, true), true))
        assertFalse(canAutoConnect(settings(AppRoute.VPN, true), false))
    }

    @Test fun `dpi only needs no vless key`() {
        assertTrue(canAutoConnect(settings(AppRoute.DPI), true))
    }

    @Test fun `no routes does not connect`() {
        assertFalse(canAutoConnect(settings(AppRoute.DIRECT), true))
    }
}
