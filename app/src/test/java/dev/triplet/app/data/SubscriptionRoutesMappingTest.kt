package dev.triplet.app.data

import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRoutesMappingTest {
    private val subscription = VlessKey(
        id = "subscription",
        name = "Subscription",
        uri = "https://subscription.example/opaque-token",
    )
    private val keys = VlessKeys(
        items = listOf(subscription),
        activeId = subscription.id,
    )
    private val vless = VlessKey(
        id = subscription.id,
        name = "VLESS",
        uri = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
            "?type=tcp&security=reality&fp=chrome&sni=translate.yandex.com" +
            "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
            "&flow=xtls-rprx-vision#MyServer",
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

    @Test fun `editing active subscription to vless changes selected kind`() {
        assertEquals(
            VpnProfileKind.VLESS,
            vpnKindAfterVlessUpdate(keys, vless, VpnProfileKind.SUBSCRIPTION),
        )
    }

    @Test fun `editing remembered key while warp is active keeps warp selected`() {
        assertEquals(
            VpnProfileKind.WARP,
            vpnKindAfterVlessUpdate(keys, vless, VpnProfileKind.WARP),
        )
    }
}
