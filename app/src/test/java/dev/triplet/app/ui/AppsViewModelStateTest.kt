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

        val state = appsUiState(
            settings,
            AppsInventoryState(listOf(app), AppsInventoryStatus.READY),
            "example",
        )
        assertEquals(listOf(app), state.loadedApps)
        assertEquals(AppRoute.VPN, state.routes[app.packageName])
        assertTrue(state.showSystemApps)
        assertEquals("example", state.query)
        assertEquals(AppsInventoryStatus.READY, state.inventoryStatus)
    }

    @Test fun `null settings keep safe defaults while initial inventory is loading`() {
        val state = appsUiState(null, AppsInventoryState(), "vpn")
        assertNull(state.loadedApps)
        assertEquals(emptyMap<String, AppRoute>(), state.routes)
        assertFalse(state.showSystemApps)
        assertEquals("vpn", state.query)
        assertEquals(AppsInventoryStatus.LOADING, state.inventoryStatus)
    }

    @Test fun `successful inventory load becomes ready`() {
        val app = AppInfo("com.example.app", "Example", false)
        val loaded = appsInventoryLoaded(listOf(app))

        assertEquals(listOf(app), loaded.apps)
        assertEquals(AppsInventoryStatus.READY, loaded.status)
    }

    @Test fun `refresh keeps a previous snapshot renderable`() {
        val app = AppInfo("com.example.app", "Example", false)
        val previous = AppsInventoryState(listOf(app), AppsInventoryStatus.READY)
        val refreshing = appsInventoryRefreshing(previous)

        assertEquals(listOf(app), refreshing.apps)
        assertEquals(AppsInventoryStatus.READY, refreshing.status)
    }

    @Test fun `inventory failure preserves the last successful snapshot`() {
        val app = AppInfo("com.example.app", "Example", false)
        val previous = AppsInventoryState(listOf(app), AppsInventoryStatus.READY)
        val failed = appsInventoryFailed(previous)

        assertEquals(listOf(app), failed.apps)
        assertEquals(AppsInventoryStatus.ERROR, failed.status)
    }

    @Test fun `initial inventory failure stays empty and recoverable`() {
        val failed = appsInventoryFailed(AppsInventoryState())

        assertNull(failed.apps)
        assertEquals(AppsInventoryStatus.ERROR, failed.status)
    }
}
