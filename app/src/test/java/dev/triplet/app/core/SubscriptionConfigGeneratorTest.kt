package dev.triplet.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionConfigGeneratorTest {
    @Test fun `subscription uses dedicated provider and manual selector group`() {
        val input = RoutingInput(
            tunFd = 7,
            apiLevel = 33,
            vpn = VpnOutbound.Subscription("https://subscription.example/opaque-token"),
            vpnApps = setOf("org.telegram.messenger"),
            vpnUids = mapOf("org.telegram.messenger" to 10101),
            dpiApps = emptySet(),
        )

        val yaml = ConfigGenerator.build(input)

        assertTrue(yaml.contains("profile:\n  store-selected: true"))
        assertTrue(yaml.contains("proxy-providers:\n  DETOUR_SUBSCRIPTION:"))
        assertTrue(yaml.contains("url: \"https://subscription.example/opaque-token\""))
        assertTrue(yaml.contains("header:\n      User-Agent:\n        - mihomo/1.19.30"))
        assertTrue(yaml.contains("- name: SUBSCRIPTION\n  type: select"))
        assertTrue(yaml.contains("use:\n    - DETOUR_SUBSCRIPTION"))
        assertTrue(yaml.contains("- UID,10101,SUBSCRIPTION"))
        assertTrue(yaml.contains("name: PROBE_SUBSCRIPTION"))
        assertTrue(yaml.contains("proxy: SUBSCRIPTION"))
        assertFalse(yaml.contains("type: vless"))
        assertFalse(yaml.contains("- name: SUBSCRIPTION\n  type: fallback"))
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
