package dev.detour.app.ui

import dev.detour.app.core.AmneziaWgOptions
import dev.detour.app.core.DpiPreset
import dev.detour.app.core.VlessKey
import dev.detour.app.core.VlessKeys
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.core.WarpProfile
import dev.detour.app.core.WarpProxy
import dev.detour.app.core.WireGuardProfiles
import dev.detour.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertTrue(state.wireGuardProfiles.isEmpty())
        assertNull(state.activeWireGuardId)
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

    @Test fun `pending WireGuard selection overrides lagging VLESS persistence`() {
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

        val state = profilesUiState(settings, selectionOverride = ProfileSelection.Warp("wg-next"))

        assertEquals(VpnProfileKind.WARP, state.activeVpn)
        assertEquals("wg-next", state.activeWireGuardId)
        assertEquals(key.id, state.activeVlessId)
        assertEquals(ProfileSelection.Vless(key.id), persistedProfileSelection(settings))
    }

    @Test fun `profile state carries WARP import status independently of settings`() {
        val state = profilesUiState(null, WarpImportStatus.IMPORTING)

        assertEquals(WarpImportStatus.IMPORTING, state.warpImportStatus)
        assertEquals(emptyList<VlessKey>(), state.vlessItems)
        assertTrue(state.wireGuardProfiles.isEmpty())
        assertNull(state.activeWireGuardId)
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
        val settings = settings(activeVpn = VpnProfileKind.VLESS, key = key)

        assertEquals(
            ProfileDeleteRequest.Vless(key.id, active = true),
            vlessDeleteRequest(settings, key.id),
        )
        assertEquals(
            ProfileDeleteRequest.Vless("other", active = false),
            vlessDeleteRequest(settings, "other"),
        )
    }

    @Test fun `delete request follows optimistic profile selection`() {
        val key = VlessKey("next", "Server", "vless://example")
        val settings = settings(activeVpn = VpnProfileKind.WARP, key = key)

        val request = vlessDeleteRequest(
            settings,
            key.id,
            selectionOverride = ProfileSelection.Vless(key.id),
        )

        assertTrue(request.active)
        assertFalse(request.failed)
    }

    @Test fun `WireGuard delete request reports optimistic active tunnel`() {
        val key = VlessKey("active", "Server", "vless://example")
        val settings = settings(activeVpn = VpnProfileKind.VLESS, key = key)

        val request = warpDeleteRequest(
            settings,
            "wg-next",
            selectionOverride = ProfileSelection.Warp("wg-next"),
        )

        assertTrue(request.active)
        assertEquals("wg-next", request.profileId)
        assertFalse(request.failed)
    }

    @Test fun `failed delete request preserves identity and becomes retryable`() {
        val request = ProfileDeleteRequest.Vless("profile", active = true)
        val failed = request.failedCopy()

        assertEquals(ProfileDeleteRequest.Vless("profile", active = true, failed = true), failed)
    }

    @Test fun `WireGuard delete request only marks selected profile active`() {
        val warp = wireGuardProfile("warp", "Cloudflare WARP")
        val awg = wireGuardProfile("awg", "My VPS")
        val settings = settings(
            activeVpn = VpnProfileKind.WARP,
            wireGuard = WireGuardProfiles(listOf(warp, awg), awg.id),
        )

        assertEquals(
            ProfileDeleteRequest.Warp(awg.id, active = true),
            warpDeleteRequest(settings, awg.id),
        )
        assertEquals(
            ProfileDeleteRequest.Warp(warp.id, active = false),
            warpDeleteRequest(settings, warp.id),
        )
    }

    @Test fun `null settings map to safe empty profile state`() {
        assertEquals(ProfilesUiState(), profilesUiState(null))
    }

    private fun settings(
        activeVpn: VpnProfileKind,
        key: VlessKey? = null,
        wireGuard: WireGuardProfiles = WireGuardProfiles.empty(),
    ) = TriSettings(
        vlessKeys = if (key == null) VlessKeys(emptyList(), null) else VlessKeys(listOf(key), key.id),
        warpProfile = wireGuard.active,
        activeVpn = activeVpn,
        preset = DpiPreset.RECOMMENDED,
        dpiCustomArgs = "",
        autoConnect = false,
        themeId = "",
        dnsId = "google",
        dnsCustom = "",
        routes = emptyMap(),
        showSystemApps = false,
        sessionStartedAt = null,
        wireGuardProfiles = wireGuard,
    )

    private fun wireGuardProfile(id: String, name: String) = WarpProfile(
        id = id,
        name = name,
        proxies = listOf(
            WarpProxy(
                name = name,
                server = "203.0.113.1",
                port = 51820,
                ip = "10.0.0.2",
                privateKey = "private-$id",
                publicKey = "public-$id",
                reserved = emptyList(),
                allowedIps = listOf("0.0.0.0/0"),
                amnezia = AmneziaWgOptions(),
            ),
        ),
    )
}
