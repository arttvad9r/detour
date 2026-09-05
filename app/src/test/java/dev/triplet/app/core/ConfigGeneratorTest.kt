package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigGeneratorTest {

    private val profile = VlessProfile(
        uuid = "test-uuid", server = "example.com", port = 443,
        sni = "translate.yandex.com", publicKey = "PBKDATA", shortId = "6BA851",
        fingerprint = "chrome", flow = "xtls-rprx-vision", name = "srv",
    )

    private val warp = WarpProfile(
        id = "warp",
        name = "Cloudflare WARP",
        proxies = listOf(
            WarpProxy(
                name = "NL",
                server = "nl.example.net",
                port = 4500,
                ip = "172.16.0.2",
                ipv6 = "2606:4700:110::2",
                privateKey = "private-key",
                publicKey = "public-key",
                reserved = listOf(1, 2, 3),
                allowedIps = listOf("0.0.0.0/0", "::/0"),
                persistentKeepalive = 25,
                dns = listOf("1.1.1.1"),
                amnezia = AmneziaWgOptions(
                    jc = 4, jmin = 40, jmax = 70,
                    s1 = 0, s2 = 0, h1 = 1, h2 = 2, h3 = 3, h4 = 4,
                    i1 = "<b 0x1234>",
                ),
            ),
        ),
    )

    private fun input(
        api: Int = 33,
        vpn: VpnOutbound? = VpnOutbound.Vless(profile),
    ) = RoutingInput(
        tunFd = 7, apiLevel = api,
        vpn = vpn,
        vpnApps = setOf("org.telegram.messenger"),
        vpnUids = mapOf("org.telegram.messenger" to 10101, "com.google.android.youtube" to 10102),
        dpiApps = setOf("com.google.android.youtube"),
    )

    @Test fun `contains vless proxy with reality opts`() {
        val yaml = ConfigGenerator.build(input())
        assertTrue(yaml.contains("- name: VLESS"))
        assertTrue(yaml.contains("type: vless"))
        assertTrue(yaml.contains("server: example.com"))
        assertTrue(yaml.contains("port: 443"))
        assertTrue(yaml.contains("uuid: test-uuid"))
        assertTrue(yaml.contains("flow: xtls-rprx-vision"))
        assertTrue(yaml.contains("client-fingerprint: chrome"))
        assertTrue(yaml.contains("public-key: PBKDATA"))
        assertTrue(yaml.contains("short-id: 6BA851"))
        assertTrue(yaml.contains("servername: translate.yandex.com"))
    }

    @Test fun `vpn apps routed to VLESS by uid`() {
        val yaml = ConfigGenerator.build(input())
        assertTrue(yaml.contains("- UID,10101,VLESS"))
        assertFalse(yaml.contains("PROCESS-NAME"))
    }

    @Test fun `warp emits stable amnezia wireguard fallback and routes vpn apps`() {
        val yaml = ConfigGenerator.build(input(vpn = VpnOutbound.Warp(warp)))
        assertTrue(yaml.contains("- name: WARP_0"))
        assertTrue(yaml.contains("type: wireguard"))
        assertTrue(yaml.contains("server: nl.example.net"))
        assertTrue(yaml.contains("port: 4500"))
        assertTrue(yaml.contains("reserved: [1, 2, 3]"))
        assertTrue(yaml.contains("allowed-ips: [0.0.0.0/0, \"::/0\"]"))
        assertTrue(yaml.contains("persistent-keepalive: 25"))
        assertTrue(yaml.contains("amnezia-wg-option:"))
        assertTrue(yaml.contains("jc: 4"))
        assertTrue(yaml.contains("i1: \"<b 0x1234>\""))
        assertTrue(yaml.contains("- name: WARP\n  type: fallback"))
        assertTrue(yaml.contains("lazy: false"))
        assertTrue(yaml.contains("expected-status: 204"))
        assertFalse(yaml.contains("type: url-test"))
        assertTrue(yaml.contains("- UID,10101,WARP"))
        assertTrue(yaml.contains("name: PROBE_WARP"))
        assertTrue(yaml.contains("proxy: WARP"))
        assertFalse(yaml.contains("type: vless"))
    }

    @Test fun `manual subscription keeps explicit selector`() {
        val yaml = ConfigGenerator.build(
            input(
                vpn = VpnOutbound.Subscription(
                    url = "https://subscription.example/token",
                    selectedNode = "Finland 2",
                    selectionMode = SubscriptionSelectionMode.MANUAL,
                ),
            ),
        )
        val group = yaml.substringAfter("- name: SUBSCRIPTION\n").substringBefore("listeners:")
        assertTrue(group.contains("type: select"))
        assertTrue(group.contains("default-selected: \"Finland 2\""))
        assertTrue(group.contains("use:\n    - DETOUR_SUBSCRIPTION"))
        assertFalse(group.contains("type: url-test"))
        assertFalse(group.contains("tolerance:"))
    }

    @Test fun `auto subscription uses hysteretic url test without pinning manual node`() {
        val yaml = ConfigGenerator.build(
            input(
                vpn = VpnOutbound.Subscription(
                    url = "https://subscription.example/token",
                    selectedNode = "Old manual node",
                    selectionMode = SubscriptionSelectionMode.AUTO,
                ),
            ),
        )
        val group = yaml.substringAfter("- name: SUBSCRIPTION\n").substringBefore("listeners:")
        assertTrue(group.contains("type: url-test"))
        assertTrue(group.contains("url: https://cp.cloudflare.com/generate_204"))
        assertTrue(group.contains("interval: 900"))
        assertTrue(group.contains("lazy: true"))
        assertTrue(group.contains("timeout: 3000"))
        assertTrue(group.contains("max-failed-times: 2"))
        assertTrue(group.contains("expected-status: 204"))
        assertTrue(group.contains("tolerance: 100"))
        assertTrue(group.contains("use:\n    - DETOUR_SUBSCRIPTION"))
        assertFalse(group.contains("default-selected:"))
        assertFalse(group.contains("Old manual node"))
    }

    @Test fun `warp uses Detour dns instead of imported proxy dns`() {
        val yaml = ConfigGenerator.build(input(vpn = VpnOutbound.Warp(warp)))
        val warpBlock = yaml.substringAfter("- name: WARP_0").substringBefore("- name: DPI")
        assertTrue(warpBlock.contains("remote-dns-resolve: false"))
        assertFalse(warpBlock.contains("dns:"))
        assertTrue(yaml.contains("nameserver:\n    - 8.8.8.8"))
    }

    @Test fun `native awg without reserved does not emit empty reserved field`() {
        val native = warp.copy(
            proxies = listOf(warp.proxies.single().copy(reserved = emptyList())),
        )
        val yaml = ConfigGenerator.build(input(vpn = VpnOutbound.Warp(native)))
        assertFalse(yaml.contains("reserved:"))
        assertTrue(yaml.contains("amnezia-wg-option:"))
        assertTrue(yaml.contains("jc: 4"))
    }

    @Test fun `dpi app gets quic reject before socks route`() {
        val yaml = ConfigGenerator.build(input())
        val quicIdx = yaml.indexOf("- AND,((UID,10102),(NETWORK,UDP),(DST-PORT,443)),REJECT")
        val dpiIdx = yaml.indexOf("- UID,10102,DPI")
        assertTrue(quicIdx >= 0)
        assertTrue(dpiIdx > quicIdx)
    }

    @Test fun `dpi socks outbound targets authenticated loopback port`() {
        val routing = input()
        val yaml = ConfigGenerator.build(routing)
        val dpiBlock = yaml.substringAfter("- name: DPI").substringBefore("listeners:")
        assertTrue(dpiBlock.contains("type: socks5"))
        assertTrue(dpiBlock.contains("server: 127.0.0.1"))
        assertTrue(dpiBlock.contains("port: 10808"))
        assertTrue(dpiBlock.contains("username: ${routing.probeCredentials.username}"))
        assertTrue(dpiBlock.contains("password: ${routing.probeCredentials.password}"))
    }

    @Test fun `last rule rejects unknown ownership`() {
        val yaml = ConfigGenerator.build(input())
        val rulesBlock = yaml.substringAfter("rules:")
        assertEquals("MATCH,REJECT", rulesBlock.trim().trimEnd(']').trim().lines().last().removePrefix("- ").trim())
    }

    @Test fun `api below 33 adds lan block rules`() {
        val yaml = ConfigGenerator.build(input(api = 30))
        assertTrue(yaml.contains("- IP-CIDR,192.168.0.0/16,REJECT,no-resolve"))
        assertFalse(yaml.contains("route-exclude-address:\n      - "))
    }

    @Test fun `api 33 plus excludes lan routes in tun`() {
        val yaml = ConfigGenerator.build(input(api = 33))
        assertTrue(yaml.contains("route-exclude-address:"))
        assertTrue(yaml.contains("192.168.0.0/16"))
        assertFalse(yaml.contains("IP-CIDR,192.168.0.0/16"))
    }

    @Test fun `without vpn no outbound vpn rules`() {
        val yaml = ConfigGenerator.build(input(vpn = null).copy(vpnApps = emptySet()))
        assertFalse(yaml.contains("type: vless"))
        assertFalse(yaml.contains("type: wireguard"))
        assertFalse(yaml.contains(",VLESS"))
        assertFalse(yaml.contains(",WARP"))
    }

    @Test fun `ipv6 is disabled and explicitly rejected`() {
        val yaml = ConfigGenerator.build(input())
        assertTrue(yaml.contains("ipv6: false"))
        assertTrue(yaml.contains("inet6-address: []"))
        assertFalse(yaml.contains("    - ::/0"))
        assertTrue(yaml.contains("- IP-CIDR6,::/0,REJECT,no-resolve"))
    }

    @Test fun `generic mixed port is not exposed`() {
        val yaml = ConfigGenerator.build(input())
        assertFalse(yaml.contains("mixed-port:"))
    }

    @Test fun `health probes are pinned and authenticated`() {
        val routing = input()
        val yaml = ConfigGenerator.build(routing)
        assertTrue(yaml.contains("name: PROBE_VLESS"))
        assertTrue(yaml.contains("port: 10810"))
        assertTrue(yaml.contains("proxy: VLESS"))
        assertTrue(yaml.contains("name: PROBE_DPI"))
        assertTrue(yaml.contains("port: 10811"))
        assertTrue(yaml.contains("proxy: DPI"))
        assertTrue(yaml.contains("username: ${routing.probeCredentials.username}"))
        assertTrue(yaml.contains("password: ${routing.probeCredentials.password}"))
        assertEquals(2, Regex("\\n  users:\\n").findAll(yaml).count())
    }

    @Test fun `whole vless output matches golden yaml`() {
        assertEquals(GOLDEN, ConfigGenerator.build(input()))
    }

    companion object {
        private val GOLDEN = """
            |mode: rule
            |log-level: info
            |ipv6: false
            |unified-delay: true
            |find-process-mode: strict
            |profile:
            |  store-selected: false
            |tun:
            |  enable: true
            |  stack: gvisor
            |  file-descriptor: 7
            |  auto-route: false
            |  auto-detect-interface: false
            |  strict-route: false
            |  mtu: 1500
            |  inet4-address:
            |    - 172.19.0.1/30
            |  inet6-address: []
            |  route-address:
            |    - 0.0.0.0/1
            |    - 128.0.0.0/1
            |  route-exclude-address:
            |    - 0.0.0.0/8
            |    - 10.0.0.0/8
            |    - 100.64.0.0/10
            |    - 127.0.0.0/8
            |    - 169.254.0.0/16
            |    - 172.16.0.0/12
            |    - 192.0.0.0/24
            |    - 192.0.2.0/24
            |    - 192.168.0.0/16
            |    - 198.18.0.0/15
            |    - 224.0.0.0/3
            |  dns-hijack:
            |    - any:53
            |dns:
            |  enable: true
            |  enhanced-mode: redir-host
            |  nameserver:
            |    - 8.8.8.8
            |proxies:
            |- name: VLESS
            |  type: vless
            |  server: example.com
            |  port: 443
            |  uuid: test-uuid
            |  network: tcp
            |  udp: true
            |  tls: true
            |  flow: xtls-rprx-vision
            |  client-fingerprint: chrome
            |  servername: translate.yandex.com
            |  reality-opts:
            |    public-key: PBKDATA
            |    short-id: 6BA851
            |- name: DPI
            |  type: socks5
            |  server: 127.0.0.1
            |  port: 10808
            |  username: ${ProbeAuth.current().username}
            |  password: ${ProbeAuth.current().password}
            |  udp: false
            |listeners:
            |- name: PROBE_VLESS
            |  type: mixed
            |  listen: 127.0.0.1
            |  port: 10810
            |  proxy: VLESS
            |  users:
            |    - username: ${ProbeAuth.current().username}
            |      password: ${ProbeAuth.current().password}
            |- name: PROBE_DPI
            |  type: mixed
            |  listen: 127.0.0.1
            |  port: 10811
            |  proxy: DPI
            |  users:
            |    - username: ${ProbeAuth.current().username}
            |      password: ${ProbeAuth.current().password}
            |rules:
            |- IP-CIDR6,::/0,REJECT,no-resolve
            |- UID,10101,VLESS
            |- AND,((UID,10102),(NETWORK,UDP),(DST-PORT,443)),REJECT
            |- UID,10102,DPI
            |- MATCH,REJECT
        """.trimMargin()
    }
}
