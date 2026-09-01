package dev.triplet.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionConfigGeneratorTest {
    @Test fun `subscription uses dedicated outbound provider and fallback group`() {
        val input = RoutingInput(
            tunFd = 7,
            apiLevel = 33,
            vpn = VpnOutbound.Subscription("https://subscription.example/opaque-token"),
            vpnApps = setOf("org.telegram.messenger"),
            vpnUids = mapOf("org.telegram.messenger" to 10101),
            dpiApps = emptySet(),
        )

        val yaml = ConfigGenerator.build(input)

        assertTrue(yaml.contains("proxy-providers:\n  DETOUR_SUBSCRIPTION:"))
        assertTrue(yaml.contains("url: \"https://subscription.example/opaque-token\""))
        assertTrue(yaml.contains("- name: SUBSCRIPTION\n  type: fallback"))
        assertTrue(yaml.contains("use:\n    - DETOUR_SUBSCRIPTION"))
        assertTrue(yaml.contains("- UID,10101,SUBSCRIPTION"))
        assertTrue(yaml.contains("name: PROBE_SUBSCRIPTION"))
        assertTrue(yaml.contains("proxy: SUBSCRIPTION"))
        assertFalse(yaml.contains("type: vless"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `subscription outbound rejects non https provider at config boundary`() {
        val input = RoutingInput(
            tunFd = 7,
            apiLevel = 33,
            vpn = VpnOutbound.Subscription("http://subscription.example/token"),
            vpnApps = setOf("org.telegram.messenger"),
            vpnUids = mapOf("org.telegram.messenger" to 10101),
            dpiApps = emptySet(),
        )

        ConfigGenerator.build(input)
    }
}
