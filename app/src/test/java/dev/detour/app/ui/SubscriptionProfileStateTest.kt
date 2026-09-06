package dev.detour.app.ui

import dev.detour.app.core.DpiPreset
import dev.detour.app.core.VlessKey
import dev.detour.app.core.VlessKeys
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionProfileStateTest {
    private val subscription = VlessKey(
        "sub",
        "subscription.example",
        "https://subscription.example/opaque-token",
    )

    private fun settings(activeVpn: VpnProfileKind) = TriSettings(
        vlessKeys = VlessKeys(listOf(subscription), subscription.id),
        warpProfile = null,
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
    )

    @Test fun `persisted subscription maps to shared key selection with subscription kind`() {
        val state = profilesUiState(settings(VpnProfileKind.SUBSCRIPTION))

        assertEquals(ProfileSelection.Vless(subscription.id), persistedProfileSelection(settings(VpnProfileKind.SUBSCRIPTION)))
        assertEquals(subscription.id, state.activeVlessId)
        assertEquals(VpnProfileKind.SUBSCRIPTION, state.activeVpn)
    }

    @Test fun `optimistic subscription selection keeps subscription kind`() {
        val state = profilesUiState(
            settings(VpnProfileKind.WARP),
            selectionOverride = ProfileSelection.Vless(subscription.id),
        )

        assertEquals(VpnProfileKind.SUBSCRIPTION, state.activeVpn)
        assertTrue(vlessDeleteRequest(settings(VpnProfileKind.SUBSCRIPTION), subscription.id).active)
    }

    @Test fun `active subscription edit restarts and delete stops tunnel`() {
        assertEquals(
            ProfileTunnelAction.RESTART,
            vlessMutationTunnelAction(
                VpnProfileKind.SUBSCRIPTION,
                subscription.id,
                subscription.id,
                deleting = false,
            ),
        )
        assertEquals(
            ProfileTunnelAction.STOP,
            vlessMutationTunnelAction(
                VpnProfileKind.SUBSCRIPTION,
                subscription.id,
                subscription.id,
                deleting = true,
            ),
        )
    }
}
