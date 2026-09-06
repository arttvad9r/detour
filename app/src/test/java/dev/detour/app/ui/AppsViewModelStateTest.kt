package dev.detour.app.ui

import dev.detour.app.core.AppRoute
import dev.detour.app.core.DpiPreset
import dev.detour.app.core.VlessKeys
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.AppInfo
import dev.detour.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsViewModelStateTest {
    @Test fun `app state combines inventory routes visibility and query`() {
        val app = AppInfo("com.example.app", "Example", false)
        val settings = settings(
            routes = mapOf(app.packageName to AppRoute.VPN),
            showSystemApps = true,
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

    @Test fun `pending system apps intent overrides lagging persistence`() {
        val state = appsUiState(
            settings = settings(showSystemApps = false),
            inventory = AppsInventoryState(emptyList(), AppsInventoryStatus.READY),
            query = "",
            showSystemOverride = true,
        )

        assertTrue(state.showSystemApps)
    }

    @Test fun `pending direct route overrides persisted vpn route`() {
        val packageName = "com.example.app"
        val state = appsUiState(
            settings = settings(routes = mapOf(packageName to AppRoute.VPN)),
            inventory = AppsInventoryState(emptyList(), AppsInventoryStatus.READY),
            query = "",
            routeOverrides = mapOf(packageName to AppRoute.DIRECT),
        )

        assertEquals(AppRoute.DIRECT, state.routes[packageName] ?: AppRoute.DIRECT)
        assertFalse(state.routes.containsKey(packageName))
    }

    @Test fun `pending dpi route overrides persisted direct route`() {
        val packageName = "com.example.app"
        val state = appsUiState(
            settings = settings(),
            inventory = AppsInventoryState(emptyList(), AppsInventoryStatus.READY),
            query = "",
            routeOverrides = mapOf(packageName to AppRoute.DPI),
        )

        assertEquals(AppRoute.DPI, state.routes[packageName])
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

    private fun settings(
        routes: Map<String, AppRoute> = emptyMap(),
        showSystemApps: Boolean = false,
    ) = TriSettings(
        vlessKeys = VlessKeys(emptyList(), null),
        warpProfile = null,
        activeVpn = VpnProfileKind.VLESS,
        preset = DpiPreset.RECOMMENDED,
        dpiCustomArgs = "",
        autoConnect = false,
        themeId = "",
        dnsId = "google",
        dnsCustom = "",
        routes = routes,
        showSystemApps = showSystemApps,
        sessionStartedAt = null,
    )
}
