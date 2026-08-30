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
        val source = TriSettings(
            vlessKeys = VlessKeys(
                items = listOf(VlessKey("id", "Profile", "vless://example")),
                activeId = "id",
            ),
            warpProfile = null,
            activeVpn = VpnProfileKind.VLESS,
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

        val state = settingsMenuUiState(source, routedCount = 3)

        assertEquals(3, state.routedCount)
        assertTrue(state.hasVless)
        assertFalse(state.hasWarp)
        assertTrue(state.autoConnect)
    }

    @Test fun `missing settings render safe defaults`() {
        assertEquals(SettingsMenuUiState(), settingsMenuUiState(null, routedCount = 0))
    }
}
