package dev.triplet.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionConfigGeneratorTest {

    private val subscription =
        (VlessKeyParser.parse("https://subscription.example/opaque-token") as ParseResult.Ok).profile

    private fun input() = RoutingInput(
        tunFd = 7,
        apiLevel = 33,
        vpn = VpnOutbound.Vless(subscription),
        vpnApps = setOf("org.telegram.messenger"),
        vpnUids = mapOf("org.telegram.messenger" to 10101),
        dpiApps = emptySet(),
    )

    @Test fun `subscription uses native mihomo provider and fallback group`() {
        val yaml = ConfigGenerator.build(input())

        assertTrue(yaml.contains("proxy-providers:"))
        assertTrue(yaml.contains("DETOUR_SUBSCRIPTION:"))
        assertTrue(yaml.contains("type: http"))
        assertTrue(yaml.contains("url: \"https://subscription.example/opaque-token\""))
        assertTrue(yaml.contains("size-limit: 4194304"))
        assertTrue(yaml.contains("health-check:"))
        assertTrue(yaml.contains("- name: SUBSCRIPTION\n  type: fallback"))
        assertTrue(yaml.contains("use:\n    - DETOUR_SUBSCRIPTION"))
        assertTrue(yaml.contains("- UID,10101,SUBSCRIPTION"))
        assertTrue(yaml.contains("name: PROBE_SUBSCRIPTION"))
        assertTrue(yaml.contains("proxy: SUBSCRIPTION"))

        assertFalse(yaml.contains("- name: VLESS"))
        assertFalse(yaml.contains("type: vless"))
        assertFalse(yaml.contains("uuid:"))
    }
}
