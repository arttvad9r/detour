package dev.detour.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiHopConfigGeneratorTest {
    private fun vless(name: String, server: String) = VlessProfile(
        uuid = "00000000-0000-4000-8000-${if (name == "entry") "000000000001" else "000000000002"}",
        server = server,
        port = 443,
        sni = "cdn.example.com",
        publicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        shortId = "0123456789abcdef",
        fingerprint = "chrome",
        flow = "xtls-rprx-vision",
        name = name,
    )

    private val warp = WarpProfile(
        id = "warp",
        name = "WARP",
        proxies = listOf(
            WarpProxy(
                name = "endpoint",
                server = "warp.example.com",
                port = 4500,
                ip = "172.16.0.2",
                privateKey = "private-key",
                publicKey = "public-key",
                reserved = listOf(1, 2, 3),
                allowedIps = listOf("0.0.0.0/0", "::/0"),
                amnezia = AmneziaWgOptions(jc = 4),
            ),
        ),
    )

    private fun input(exit: VpnOutbound, entry: VpnOutbound) = RoutingInput(
        tunFd = 7,
        apiLevel = 33,
        vpn = exit,
        vpnApps = setOf("example.app"),
        vpnUids = mapOf("example.app" to 10101),
        dpiApps = emptySet(),
        chainEntry = entry,
    )

    @Test fun `vless exit dials through vless entry without recursive entry dialer`() {
        val yaml = ConfigGenerator.build(
            input(
                exit = VpnOutbound.Vless(vless("exit", "exit.example.com")),
                entry = VpnOutbound.Vless(vless("entry", "entry.example.com")),
            ),
        )

        val entryBlock = yaml.substringAfter("- name: ENTRY_VLESS").substringBefore("- name: VLESS")
        val exitBlock = yaml.substringAfter("- name: VLESS").substringBefore("- name: DPI")
        assertTrue(entryBlock.contains("server: entry.example.com"))
        assertFalse(entryBlock.contains("dialer-proxy:"))
        assertTrue(exitBlock.contains("server: exit.example.com"))
        assertTrue(exitBlock.contains("dialer-proxy: ENTRY_VLESS"))
        assertTrue(yaml.contains("proxy: VLESS"))
    }

    @Test fun `subscription provider proxies dial through warp entry group`() {
        val yaml = ConfigGenerator.build(
            input(
                exit = VpnOutbound.Subscription("https://subscription.example/token"),
                entry = VpnOutbound.Warp(warp),
            ),
        )

        val provider = yaml.substringAfter("DETOUR_SUBSCRIPTION:").substringBefore("proxy-groups:")
        assertTrue(yaml.contains("- name: ENTRY_WARP_0"))
        assertTrue(yaml.contains("- name: ENTRY_WARP\n  type: fallback"))
        assertTrue(provider.contains("override:"))
        assertTrue(provider.contains("dialer-proxy: ENTRY_WARP"))
        assertTrue(yaml.contains("- name: SUBSCRIPTION"))
        assertTrue(yaml.contains("proxy: SUBSCRIPTION"))
    }

    @Test fun `warp exit concrete proxies dial through vless entry`() {
        val yaml = ConfigGenerator.build(
            input(
                exit = VpnOutbound.Warp(warp),
                entry = VpnOutbound.Vless(vless("entry", "entry.example.com")),
            ),
        )

        val exitBlock = yaml.substringAfter("- name: WARP_0").substringBefore("- name: DPI")
        assertTrue(exitBlock.contains("dialer-proxy: ENTRY_VLESS"))
        assertTrue(yaml.contains("- name: WARP\n  type: fallback"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `subscription cannot be entry`() {
        ConfigGenerator.build(
            input(
                exit = VpnOutbound.Vless(vless("exit", "exit.example.com")),
                entry = VpnOutbound.Subscription("https://subscription.example/token"),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `warp cannot be both entry and exit`() {
        ConfigGenerator.build(input(exit = VpnOutbound.Warp(warp), entry = VpnOutbound.Warp(warp)))
    }
}
