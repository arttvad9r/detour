package dev.detour.app.ui

import androidx.window.core.layout.WindowSizeClass
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.core.AppRoute
import dev.detour.app.core.DpiPreset
import dev.detour.app.core.VlessKeys
import dev.detour.app.data.TriSettings
import dev.detour.app.vpn.EffectiveRoutes
import dev.detour.app.vpn.VpnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePresentationTest {
    @Test fun `route summary includes implicit Direct apps but excludes removed assignments`() {
        val settings = summarySettings(mapOf("vpn" to AppRoute.VPN, "dpi" to AppRoute.DPI, "removed" to AppRoute.VPN))
        val state = homeUiState(
            settings, VpnState.Idle, EffectiveRoutes(setOf("vpn"), setOf("dpi")),
            installedPackages = setOf("bank", "browser", "vpn", "dpi"),
        )
        assertEquals(2, state.directCount)
        assertEquals(1, state.vpnCount)
        assertEquals(1, state.dpiCount)
        assertEquals(2, state.routedCount)
    }

    @Test fun `rejected route assignments are not presented as Direct`() {
        val state = homeUiState(
            summarySettings(mapOf("vpn" to AppRoute.VPN)), VpnState.Idle,
            EffectiveRoutes(emptySet(), emptySet(), setOf(10001)),
            installedPackages = setOf("vpn", "bank"),
        )
        assertEquals(1, state.directCount)
        assertEquals(0, state.vpnCount)
    }

    @Test fun `missing app inventory does not claim zero Direct apps`() {
        assertEquals(null, homeUiState(null, VpnState.Idle, EffectiveRoutes(emptySet(), emptySet())).directCount)
    }

    private fun summarySettings(routes: Map<String, AppRoute>) = TriSettings(
        vlessKeys = VlessKeys(emptyList(), null), warpProfile = null, activeVpn = VpnProfileKind.VLESS,
        preset = DpiPreset.RECOMMENDED, dpiCustomArgs = "", autoConnect = false,
        themeId = "detour_dark", dnsId = "google", dnsCustom = "", routes = routes,
        showSystemApps = false, sessionStartedAt = null,
    )

    @Test fun `protocol describes effective transports`() {
        assertEquals(
            HomeProtocol.VLESS_DPI,
            homeProtocol(EffectiveRoutes(vpnPackages = setOf("vpn"), dpiPackages = setOf("dpi"))),
        )
        assertEquals(
            HomeProtocol.DPI,
            homeProtocol(EffectiveRoutes(vpnPackages = emptySet(), dpiPackages = setOf("dpi"))),
        )
        assertEquals(
            HomeProtocol.VLESS,
            homeProtocol(EffectiveRoutes(vpnPackages = setOf("vpn"), dpiPackages = emptySet())),
        )
        assertEquals(HomeProtocol.NONE, homeProtocol(EffectiveRoutes(emptySet(), emptySet())))
    }

    @Test fun `rejected shared uid routes do not advertise a transport`() {
        val rejected = EffectiveRoutes(emptySet(), emptySet(), sharedUidConflict = setOf(10001))
        assertEquals(HomeProtocol.NONE, homeProtocol(rejected))
    }

    @Test fun `profile presentation follows selected profile kind`() {
        val vless =
            "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
                "?type=tcp&security=reality&fp=chrome&sni=translate.yandex.com" +
                "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
                "&flow=xtls-rprx-vision#MyServer"

        assertEquals(
            HomeProfilePresentation(name = "MyServer", server = "example.com"),
            homeProfilePresentation(VpnProfileKind.VLESS, vless, "Warp", 8),
        )
        assertEquals(
            HomeProfilePresentation(name = "Warp", server = null, endpointCount = 8),
            homeProfilePresentation(VpnProfileKind.WARP, vless, "Warp", 8),
        )
    }

    @Test fun `home split layout starts at expanded width`() {
        assertFalse(homeUsesSplitLayout(WindowSizeClass(839, 900)))
        assertTrue(homeUsesSplitLayout(WindowSizeClass(840, 900)))
        assertTrue(homeUsesSplitLayout(WindowSizeClass(1600, 900)))
    }

    @Test fun `session elapsed formatting is stable and clamps negatives`() {
        assertEquals("00:00:00", formatSessionElapsed(-1))
        assertEquals("00:00:59", formatSessionElapsed(59))
        assertEquals("01:01:01", formatSessionElapsed(3661))
    }

    @Test fun `notification permission is not requested before Android 13`() {
        assertEquals(
            NotificationPermissionPrompt.NONE,
            notificationPermissionPrompt(
                sdkInt = 32,
                granted = false,
                shouldShowRationale = false,
                directRequestAttempted = false,
                rationaleShown = false,
            ),
        )
    }

    @Test fun `granted notification permission needs no prompt`() {
        assertEquals(
            NotificationPermissionPrompt.NONE,
            notificationPermissionPrompt(
                sdkInt = 33,
                granted = true,
                shouldShowRationale = false,
                directRequestAttempted = false,
                rationaleShown = false,
            ),
        )
    }

    @Test fun `first eligible notification permission prompt requests directly`() {
        assertEquals(
            NotificationPermissionPrompt.REQUEST,
            notificationPermissionPrompt(
                sdkInt = 33,
                granted = false,
                shouldShowRationale = false,
                directRequestAttempted = false,
                rationaleShown = false,
            ),
        )
    }

    @Test fun `notification denial with rationale uses contextual explanation`() {
        assertEquals(
            NotificationPermissionPrompt.RATIONALE,
            notificationPermissionPrompt(
                sdkInt = 33,
                granted = false,
                shouldShowRationale = true,
                directRequestAttempted = true,
                rationaleShown = false,
            ),
        )
    }

    @Test fun `notification prompt is not repeated after rationale in same session`() {
        assertEquals(
            NotificationPermissionPrompt.NONE,
            notificationPermissionPrompt(
                sdkInt = 33,
                granted = false,
                shouldShowRationale = true,
                directRequestAttempted = true,
                rationaleShown = true,
            ),
        )
    }
}
