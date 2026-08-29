package dev.triplet.app.ui

import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfilesViewModelStateTest {
    @Test fun `profile state exposes active selection and list`() {
        val key = VlessKey("active", "Server", "vless://example")
        val settings = TriSettings(
            vlessKeys = VlessKeys(listOf(key), key.id),
            warpProfile = null,
            activeVpn = VpnProfileKind.VLESS,
            preset = DpiPreset.RECOMMENDED,
            dpiCustomArgs = "",
            autoConnect = false,
            themeId = "",
            dnsId = "google",
            dnsCustom = "",
            routes = emptyMap(),
            showSystemApps = false,
            sessionStartedAt = null,
        )

        val state = profilesUiState(settings)
        assertEquals(listOf(key), state.vlessItems)
        assertEquals(key.id, state.activeVlessId)
        assertEquals(VpnProfileKind.VLESS, state.activeVpn)
        assertNull(state.warpProfile)
    }

    @Test fun `null settings map to safe empty profile state`() {
        assertEquals(ProfilesUiState(), profilesUiState(null))
    }
}
