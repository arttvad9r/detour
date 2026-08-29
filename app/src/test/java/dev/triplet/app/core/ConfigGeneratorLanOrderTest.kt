package dev.triplet.app.core

import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigGeneratorLanOrderTest {
    private val profile = VlessProfile(
        uuid = "test-uuid",
        server = "example.com",
        port = 443,
        sni = "example.com",
        publicKey = "PBKDATA",
        shortId = "6BA851",
        fingerprint = "chrome",
        flow = "xtls-rprx-vision",
        name = "srv",
    )

    @Test fun `api below 33 rejects LAN before routed UID rules`() {
        val yaml = ConfigGenerator.build(
            RoutingInput(
                tunFd = 7,
                apiLevel = 30,
                vpn = VpnOutbound.Vless(profile),
                vpnApps = setOf("org.telegram.messenger"),
                vpnUids = mapOf(
                    "org.telegram.messenger" to 10101,
                    "com.google.android.youtube" to 10102,
                ),
                dpiApps = setOf("com.google.android.youtube"),
            ),
        )

        val lan = yaml.indexOf("- IP-CIDR,192.168.0.0/16,REJECT,no-resolve")
        val vpn = yaml.indexOf("- UID,10101,VLESS")
        val dpi = yaml.indexOf("- UID,10102,DPI")

        assertTrue(lan >= 0)
        assertTrue(vpn > lan)
        assertTrue(dpi > lan)
    }
}
