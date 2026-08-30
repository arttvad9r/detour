package dev.triplet.app.ui

import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.TriSettings
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
        assertFalse(state.hasWarp)
        assertTrue(state.autoConnect)
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

    private fun settings(autoConnect: Boolean, withVless: Boolean = false) = TriSettings(
        vlessKeys = if (withVless) {
            VlessKeys(
                items = listOf(VlessKey("id", "Profile", "vless://example")),
                activeId = "id",
            )
        } else {
            VlessKeys(emptyList(), null)
        },
        warpProfile = null,
        activeVpn = VpnProfileKind.VLESS,
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
