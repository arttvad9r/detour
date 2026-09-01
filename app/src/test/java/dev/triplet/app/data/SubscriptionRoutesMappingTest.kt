package dev.triplet.app.data

import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRoutesMappingTest {
    private val keys = VlessKeys(
        items = listOf(
            VlessKey(
                id = "subscription",
                name = "Subscription",
                uri = "https://subscription.example/opaque-token",
            ),
        ),
        activeId = "subscription",
    )

    @Test fun `subscription kind is configured only for subscription key`() {
        val settings = RoutesMapping.toSettings(
            mapOf(
                "vless_keys" to keys.toJson(),
                "vpn_profile_kind" to VpnProfileKind.SUBSCRIPTION.name,
            ),
        )

        assertEquals(VpnProfileKind.SUBSCRIPTION, settings.activeVpn)
        assertEquals("https://subscription.example/opaque-token", settings.vlessUri)
        assertTrue(settings.activeVpnConfigured)
    }

    @Test fun `subscription key fails closed when stored kind says vless`() {
        val settings = RoutesMapping.toSettings(
            mapOf(
                "vless_keys" to keys.toJson(),
                "vpn_profile_kind" to VpnProfileKind.VLESS.name,
            ),
        )

        assertEquals(VpnProfileKind.VLESS, settings.activeVpn)
        assertFalse(settings.activeVpnConfigured)
    }
}
