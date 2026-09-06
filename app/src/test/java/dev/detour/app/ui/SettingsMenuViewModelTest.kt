package dev.detour.app.ui

import dev.detour.app.core.DpiPreset
import dev.detour.app.core.VlessKey
import dev.detour.app.core.VlessKeys
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMenuViewModelTest {
    @Test fun `presentation state summarizes settings without exposing persistence`() {
        val source = settings(autoConnect = true, withVless = true)

        val state = settingsMenuUiState(source, routedCount = 3)

        assertEquals(3, state.routedCount)
        assertTrue(state.hasVless)
        assertFalse(state.hasSubscription)
        assertFalse(state.hasWarp)
        assertTrue(state.autoConnect)
    }

    @Test fun `subscription profile is not reported as vless`() {
        val state = settingsMenuUiState(
            settings = settings(autoConnect = false, withSubscription = true),
            routedCount = 0,
        )

        assertFalse(state.hasVless)
        assertTrue(state.hasSubscription)
    }

    @Test fun `pending auto connect intent overrides lagging persistence`() {
        val state = settingsMenuUiState(
            settings = settings(autoConnect = false),
            routedCount = 0,
            autoConnectOverride = true,
        )

        assertTrue(state.autoConnect)
    }

    @Test fun `latest pending disable overrides persisted enabled value`() {
        val state = settingsMenuUiState(
            settings = settings(autoConnect = true),
            routedCount = 0,
            autoConnectOverride = false,
        )

        assertFalse(state.autoConnect)
    }

    @Test fun `missing settings render safe defaults`() {
        assertEquals(SettingsMenuUiState(), settingsMenuUiState(null, routedCount = 0))
    }

    private fun settings(
        autoConnect: Boolean,
        withVless: Boolean = false,
        withSubscription: Boolean = false,
    ): TriSettings {
        val items = buildList {
            if (withVless) add(VlessKey("vless-id", "VLESS", "vless://example"))
            if (withSubscription) {
                add(VlessKey("subscription-id", "Subscription", "https://subscription.example/profile"))
            }
        }
        return TriSettings(
            vlessKeys = VlessKeys(items = items, activeId = items.firstOrNull()?.id),
            warpProfile = null,
            activeVpn = if (withSubscription && !withVless) {
                VpnProfileKind.SUBSCRIPTION
            } else {
                VpnProfileKind.VLESS
            },
            preset = DpiPreset.RECOMMENDED,
            dpiCustomArgs = "",
            autoConnect = autoConnect,
            themeId = "",
            dnsId = "google",
            dnsCustom = "",
            routes = emptyMap(),
            showSystemApps = false,
            sessionStartedAt = null,
        )
    }
}
