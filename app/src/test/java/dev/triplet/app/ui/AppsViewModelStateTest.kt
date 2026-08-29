package dev.triplet.app.ui

import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.AppInfo
import dev.triplet.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsViewModelStateTest {
    @Test fun `app state combines inventory routes visibility and query`() {
        val app = AppInfo("com.example.app", "Example", false)
        val settings = TriSettings(
            vlessKeys = VlessKeys(emptyList(), null),
            warpProfile = null,
            activeVpn = VpnProfileKind.VLESS,
            preset = DpiPreset.RECOMMENDED,
            dpiCustomArgs = "",
            autoConnect = false,
            themeId = "",
            dnsId = "google",
            dnsCustom = "",
            routes = mapOf(app.packageName to AppRoute.VPN),
            showSystemApps = true,
            sessionStartedAt = null,
        )

        val state = appsUiState(settings, listOf(app), "example")
        assertEquals(listOf(app), state.loadedApps)
        assertEquals(AppRoute.VPN, state.routes[app.packageName])
        assertTrue(state.showSystemApps)
        assertEquals("example", state.query)
    }

    @Test fun `null settings keep safe route defaults while preserving query`() {
        val state = appsUiState(null, null, "vpn")
        assertNull(state.loadedApps)
        assertEquals(emptyMap<String, AppRoute>(), state.routes)
        assertFalse(state.showSystemApps)
        assertEquals("vpn", state.query)
    }
}
