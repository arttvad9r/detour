package dev.triplet.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationRuleConfigTest {
    private val profile = VlessProfile(
        uuid = "test-uuid",
        server = "vpn.example",
        port = 443,
        sni = "vpn.example",
        publicKey = "PBKDATA",
        shortId = "6BA851",
        fingerprint = "chrome",
        flow = "xtls-rprx-vision",
        name = "VPN",
    )

    private fun rule(type: DestinationRuleType, value: String, route: AppRoute) =
        requireNotNull(DestinationRules.create(type, value, route))

    @Test fun `destination overrides compile before base uid routes`() {
        val yaml = ConfigGenerator.build(
            RoutingInput(
                tunFd = 7,
                apiLevel = 33,
                vpn = VpnOutbound.Vless(profile),
                vpnApps = setOf("app.vpn"),
                vpnUids = mapOf("app.vpn" to 10101, "app.dpi" to 10102),
                dpiApps = setOf("app.dpi"),
                destinationRules = listOf(
                    rule(DestinationRuleType.DOMAIN_SUFFIX, "example.com", AppRoute.DIRECT),
                    rule(DestinationRuleType.IP_CIDR, "203.0.113.0/24", AppRoute.VPN),
                ),
            ),
        )

        val suffix = yaml.indexOf("- DOMAIN-SUFFIX,example.com,DIRECT")
        val cidr = yaml.indexOf("- IP-CIDR,203.0.113.0/24,VLESS")
        val uid = yaml.indexOf("- UID,10101,VLESS")
        assertTrue(suffix >= 0)
        assertTrue(cidr > suffix)
        assertTrue(uid > cidr)
        assertFalse(yaml.contains("IP-CIDR,203.0.113.0/24,VLESS,no-resolve"))
    }

    @Test fun `dpi destination override rejects quic before socks routing`() {
        val yaml = ConfigGenerator.build(
            RoutingInput(
                tunFd = 7,
                apiLevel = 33,
                vpn = VpnOutbound.Vless(profile),
                vpnApps = setOf("app.vpn"),
                vpnUids = mapOf("app.vpn" to 10101),
                dpiApps = emptySet(),
                destinationRules = listOf(
                    rule(DestinationRuleType.DOMAIN, "video.example", AppRoute.DPI),
                ),
            ),
        )

        val reject = yaml.indexOf(
            "- AND,((DOMAIN,video.example),(NETWORK,UDP),(DST-PORT,443)),REJECT",
        )
        val dpi = yaml.indexOf("- DOMAIN,video.example,DPI")
        assertTrue(reject >= 0)
        assertTrue(dpi > reject)
        assertTrue(yaml.contains("name: PROBE_DPI"))
    }

    @Test fun `direct override does not require vpn profile`() {
        val yaml = ConfigGenerator.build(
            RoutingInput(
                tunFd = 7,
                apiLevel = 33,
                vpn = null,
                vpnApps = emptySet(),
                vpnUids = mapOf("app.dpi" to 10102),
                dpiApps = setOf("app.dpi"),
                destinationRules = listOf(
                    rule(DestinationRuleType.DOMAIN, "direct.example", AppRoute.DIRECT),
                ),
            ),
        )

        assertTrue(yaml.contains("- DOMAIN,direct.example,DIRECT"))
        assertFalse(yaml.contains("name: PROBE_VLESS"))
    }

    @Test fun `vpn override requires configured vpn profile`() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigGenerator.build(
                RoutingInput(
                    tunFd = 7,
                    apiLevel = 33,
                    vpn = null,
                    vpnApps = emptySet(),
                    vpnUids = mapOf("app.dpi" to 10102),
                    dpiApps = setOf("app.dpi"),
                    destinationRules = listOf(
                        rule(DestinationRuleType.DOMAIN, "vpn.example", AppRoute.VPN),
                    ),
                ),
            )
        }
    }

    @Test fun `pre api 33 lan safety rules remain above destination overrides`() {
        val yaml = ConfigGenerator.build(
            RoutingInput(
                tunFd = 7,
                apiLevel = 30,
                vpn = VpnOutbound.Vless(profile),
                vpnApps = setOf("app.vpn"),
                vpnUids = mapOf("app.vpn" to 10101),
                dpiApps = emptySet(),
                destinationRules = listOf(
                    rule(DestinationRuleType.IP_CIDR, "10.0.0.0/8", AppRoute.DIRECT),
                ),
            ),
        )

        val safety = yaml.indexOf("- IP-CIDR,10.0.0.0/8,REJECT,no-resolve")
        val custom = yaml.indexOf("- IP-CIDR,10.0.0.0/8,DIRECT")
        assertTrue(safety >= 0)
        assertTrue(custom > safety)
    }
}
