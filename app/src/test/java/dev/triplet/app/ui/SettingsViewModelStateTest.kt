package dev.triplet.app.ui

import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelStateTest {
    private fun settings(
        dnsId: String = "cloudflare",
        dnsCustom: String = "https://dns.example/dns-query",
        preset: DpiPreset = DpiPreset.RECOMMENDED,
        dpiCustomArgs: String = "-d 1 -s 2",
    ) = TriSettings(
        vlessKeys = VlessKeys(emptyList(), null),
        warpProfile = null,
        activeVpn = VpnProfileKind.VLESS,
        preset = preset,
        dpiCustomArgs = dpiCustomArgs,
        autoConnect = false,
        themeId = "",
        dnsId = dnsId,
        dnsCustom = dnsCustom,
        routes = emptyMap(),
        showSystemApps = false,
        sessionStartedAt = null,
    )

    @Test fun `dns state uses persisted selection until editor overrides it`() {
        val persisted = dnsUiState(settings(), customDraft = null, editingOverride = null)
        assertEquals("cloudflare", persisted.selectedDns)
        assertEquals("https://dns.example/dns-query", persisted.customField)
        assertFalse(persisted.editingCustom)
        assertTrue(persisted.canSaveCustom)

        val editing = dnsUiState(
            settings(),
            customDraft = "not-a-resolver",
            editingOverride = true,
        )
        assertTrue(editing.editingCustom)
        assertTrue(editing.customInvalid)
        assertFalse(editing.canSaveCustom)
    }

    @Test fun `dns blank persisted id falls back to google`() {
        assertEquals("google", dnsUiState(settings(dnsId = ""), null, null).selectedDns)
    }

    @Test fun `dpi draft overrides persisted args and validates independently`() {
        val persisted = dpiUiState(settings(), customDraft = null, editingOverride = null)
        assertEquals(DpiPreset.RECOMMENDED, persisted.preset)
        assertEquals("-d 1 -s 2", persisted.customField)
        assertFalse(persisted.editingCustom)
        assertTrue(persisted.canSaveCustom)

        val invalid = dpiUiState(
            settings(),
            customDraft = "--daemon yes",
            editingOverride = true,
        )
        assertTrue(invalid.editingCustom)
        assertTrue(invalid.customInvalid)
        assertFalse(invalid.canSaveCustom)
    }

    @Test fun `persisted custom dpi opens editor`() {
        val state = dpiUiState(settings(preset = DpiPreset.CUSTOM), null, null)
        assertTrue(state.editingCustom)
    }
}
