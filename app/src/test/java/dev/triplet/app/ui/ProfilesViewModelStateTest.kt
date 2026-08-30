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
        assertEquals(WarpImportStatus.IDLE, state.warpImportStatus)
        assertEquals(VlessSaveStatus.IDLE, state.vlessSaveStatus)
    }

    @Test fun `pending VLESS selection overrides lagging WARP persistence`() {
        val key = VlessKey("next", "Server", "vless://example")
        val settings = TriSettings(
            vlessKeys = VlessKeys(listOf(key), key.id),
            warpProfile = null,
            activeVpn = VpnProfileKind.WARP,
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

        val state = profilesUiState(
            settings,
            selectionOverride = ProfileSelection.Vless(key.id),
        )

        assertEquals(VpnProfileKind.VLESS, state.activeVpn)
        assertEquals(key.id, state.activeVlessId)
    }

    @Test fun `pending WARP selection overrides lagging VLESS persistence`() {
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

        val state = profilesUiState(settings, selectionOverride = ProfileSelection.Warp)

        assertEquals(VpnProfileKind.WARP, state.activeVpn)
        assertEquals(key.id, state.activeVlessId)
        assertEquals(ProfileSelection.Vless(key.id), persistedProfileSelection(settings))
    }

    @Test fun `profile state carries WARP import status independently of settings`() {
        val state = profilesUiState(null, WarpImportStatus.IMPORTING)

        assertEquals(WarpImportStatus.IMPORTING, state.warpImportStatus)
        assertEquals(emptyList<VlessKey>(), state.vlessItems)
        assertNull(state.warpProfile)
    }

    @Test fun `VLESS save gate blocks duplicate submit while saving`() {
        assertEquals(true, canStartVlessSave(VlessSaveStatus.IDLE))
        assertEquals(false, canStartVlessSave(VlessSaveStatus.SAVING))
        assertEquals(false, canStartVlessSave(VlessSaveStatus.SAVED))
        assertEquals(true, canStartVlessSave(VlessSaveStatus.ERROR))

        val state = profilesUiState(null, vlessSaveStatus = VlessSaveStatus.ERROR)
        assertEquals(VlessSaveStatus.ERROR, state.vlessSaveStatus)
        val savedState = profilesUiState(null, vlessSaveStatus = VlessSaveStatus.SAVED)
        assertEquals(VlessSaveStatus.SAVED, savedState.vlessSaveStatus)
    }

    @Test fun `VLESS delete request reports whether the profile is active`() {
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

        assertEquals(
            ProfileDeleteRequest.Vless(key.id, active = true),
            vlessDeleteRequest(settings, key.id),
        )
        assertEquals(
            ProfileDeleteRequest.Vless("other", active = false),
            vlessDeleteRequest(settings, "other"),
        )
    }

    @Test fun `WARP delete request reports active tunnel`() {
        val settings = TriSettings(
            vlessKeys = VlessKeys(emptyList(), null),
            warpProfile = null,
            activeVpn = VpnProfileKind.WARP,
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

        assertEquals(ProfileDeleteRequest.Warp(active = true), warpDeleteRequest(settings))
    }

    @Test fun `null settings map to safe empty profile state`() {
        assertEquals(ProfilesUiState(), profilesUiState(null))
    }
}
