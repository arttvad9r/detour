package dev.triplet.app.ui

import dev.triplet.app.core.DnsOptions
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsViewModelStateTest {
    @Test fun `saving state disables duplicate custom save`() {
        val state = dnsUiState(
            settings = null,
            customDraft = "1.1.1.1",
            editingOverride = true,
            saveState = DnsSaveState.SAVING,
        )

        assertEquals(DnsSaveState.SAVING, state.saveState)
        assertFalse(state.canSaveCustom)
    }

    @Test fun `failed custom save remains retryable`() {
        val state = dnsUiState(
            settings = null,
            customDraft = "1.1.1.1",
            editingOverride = true,
            saveState = DnsSaveState.ERROR,
        )

        assertEquals(DnsSaveState.ERROR, state.saveState)
        assertTrue(state.canSaveCustom)
    }

    @Test fun `pending known selection overrides lagging custom persistence`() {
        val state = dnsUiState(
            settings = settings(DnsOptions.CUSTOM, "1.1.1.1"),
            customDraft = null,
            editingOverride = null,
            selectionOverride = "google",
        )

        assertEquals("google", state.selectedDns)
        assertFalse(state.editingCustom)
    }

    @Test fun `pending custom selection overrides lagging known persistence`() {
        val state = dnsUiState(
            settings = settings("google", "1.1.1.1"),
            customDraft = "8.8.8.8",
            editingOverride = true,
            selectionOverride = DnsOptions.CUSTOM,
        )

        assertEquals(DnsOptions.CUSTOM, state.selectedDns)
        assertTrue(state.editingCustom)
        assertTrue(state.customChanged)
    }

    @Test fun `cleared draft follows later persisted custom value`() {
        val state = dnsUiState(
            settings = settings(DnsOptions.CUSTOM, "9.9.9.9"),
            customDraft = null,
            editingOverride = null,
        )

        assertEquals("9.9.9.9", state.customField)
        assertFalse(state.customChanged)
    }

    private fun settings(dnsId: String, dnsCustom: String) = TriSettings(
        vlessKeys = VlessKeys(emptyList(), null),
        warpProfile = null,
        activeVpn = VpnProfileKind.VLESS,
        preset = DpiPreset.RECOMMENDED,
        dpiCustomArgs = "",
        autoConnect = false,
        themeId = "",
        dnsId = dnsId,
        dnsCustom = dnsCustom,
        routes = emptyMap(),
        showSystemApps = false,
        sessionStartedAt = null,
    )
}
