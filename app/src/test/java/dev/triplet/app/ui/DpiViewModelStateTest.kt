package dev.triplet.app.ui

import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiViewModelStateTest {
    @Test fun `saving state disables duplicate custom save`() {
        val state = dpiUiState(
            settings = null,
            customDraft = "-d 1 -s 2",
            editingOverride = true,
            saveState = DpiSaveState.SAVING,
        )

        assertEquals(DpiSaveState.SAVING, state.saveState)
        assertFalse(state.canSaveCustom)
    }

    @Test fun `failed custom save remains retryable`() {
        val state = dpiUiState(
            settings = null,
            customDraft = "-d 1 -s 2",
            editingOverride = true,
            saveState = DpiSaveState.ERROR,
        )

        assertEquals(DpiSaveState.ERROR, state.saveState)
        assertTrue(state.canSaveCustom)
    }

    @Test fun `pending recommended selection overrides lagging custom persistence`() {
        val state = dpiUiState(
            settings = settings(DpiPreset.CUSTOM, "-d 1 -s 2"),
            customDraft = null,
            editingOverride = null,
            presetOverride = DpiPreset.RECOMMENDED,
        )

        assertEquals(DpiPreset.RECOMMENDED, state.preset)
        assertFalse(state.editingCustom)
    }

    @Test fun `pending custom selection overrides lagging recommended persistence`() {
        val state = dpiUiState(
            settings = settings(DpiPreset.RECOMMENDED, "-d 1 -s 2"),
            customDraft = "-d 3 -s 4",
            editingOverride = true,
            presetOverride = DpiPreset.CUSTOM,
        )

        assertEquals(DpiPreset.CUSTOM, state.preset)
        assertTrue(state.editingCustom)
        assertTrue(state.customChanged)
    }

    @Test fun `cleared draft follows later persisted custom args`() {
        val state = dpiUiState(
            settings = settings(DpiPreset.CUSTOM, "-d 5 -s 6"),
            customDraft = null,
            editingOverride = null,
        )

        assertEquals("-d 5 -s 6", state.customField)
        assertFalse(state.customChanged)
    }

    private fun settings(preset: DpiPreset, customArgs: String) = TriSettings(
        vlessKeys = VlessKeys(emptyList(), null),
        warpProfile = null,
        activeVpn = VpnProfileKind.VLESS,
        preset = preset,
        dpiCustomArgs = customArgs,
        autoConnect = false,
        themeId = "",
        dnsId = "google",
        dnsCustom = "",
        routes = emptyMap(),
        showSystemApps = false,
        sessionStartedAt = null,
    )
}
