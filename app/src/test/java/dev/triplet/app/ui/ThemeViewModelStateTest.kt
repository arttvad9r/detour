package dev.triplet.app.ui

import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeViewModelStateTest {
    @Test fun `persisted theme is exposed as screen state`() {
        val persisted = AppTheme.entries.last()

        val state = themeUiState(settings(persisted.id))

        assertEquals(persisted.id, state.selectedThemeId)
    }

    @Test fun `pending theme intent overrides lagging persistence`() {
        val persisted = AppTheme.entries.first()
        val desired = AppTheme.entries.last()

        val state = themeUiState(
            settings = settings(persisted.id),
            themeOverride = desired.id,
        )

        assertEquals(desired.id, state.selectedThemeId)
    }

    @Test fun `missing settings use canonical default theme`() {
        assertEquals(AppTheme.byId("").id, themeUiState(null).selectedThemeId)
    }

    private fun settings(themeId: String) = TriSettings(
        vlessKeys = VlessKeys(emptyList(), null),
        warpProfile = null,
        activeVpn = VpnProfileKind.VLESS,
        preset = DpiPreset.RECOMMENDED,
        dpiCustomArgs = "",
        autoConnect = false,
        themeId = themeId,
        dnsId = "google",
        dnsCustom = "",
        routes = emptyMap(),
        showSystemApps = false,
        sessionStartedAt = null,
    )
}
