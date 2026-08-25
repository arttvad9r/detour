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

    private fun input(
        api: Int = 33,
        prof: VlessProfile? = profile,
    ) = RoutingInput(
        tunFd = 7, apiLevel = api,
        profile = prof,
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

    @Test fun `dpi app gets quic reject before socks route`() {
        val yaml = ConfigGenerator.build(input())
        val quicIdx = yaml.indexOf("- AND,((UID,10102),(NETWORK,UDP),(DST-PORT,443)),REJECT")
        val dpiIdx = yaml.indexOf("- UID,10102,DPI")
        assertTrue(quicIdx >= 0)
        assertTrue(dpiIdx > quicIdx) // QUIC-block строго раньше общего правила DPI
    }

    @Test fun `dpi socks outbound targets loopback port`() {
        val yaml = ConfigGenerator.build(input())
        assertTrue(yaml.contains("- name: DPI"))
        assertTrue(yaml.contains("type: socks5"))
        assertTrue(yaml.contains("server: 127.0.0.1"))
        assertTrue(yaml.contains("port: 10808"))
    }

    @Test fun `last rule is MATCH DIRECT`() {
        val yaml = ConfigGenerator.build(input())
        val rulesBlock = yaml.substringAfter("rules:")
        assertEquals("MATCH,DIRECT", rulesBlock.trim().trimEnd(']').trim().lines().last().removePrefix("- ").trim())
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

    @Test fun `without profile no vless outbound and no vless rules`() {
        val yaml = ConfigGenerator.build(input(prof = null))
        assertFalse(yaml.contains("type: vless"))
        assertFalse(yaml.contains(",VLESS"))
    }

    @Test fun `health mixed port bound to loopback`() {
        val yaml = ConfigGenerator.build(input())
        assertTrue(yaml.contains("mixed-port: 10809"))
        assertTrue(yaml.contains("bind-address: 127.0.0.1"))
    }

    @Test fun `whole output matches golden yaml`() {
        assertEquals(GOLDEN, ConfigGenerator.build(input()))
    }

    companion object {
        private val GOLDEN = """
            |mode: rule
            |log-level: info
            |ipv6: false
            |find-process-mode: strict
            |mixed-port: 10809
            |bind-address: 127.0.0.1
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
            |  inet6-address:
            |    - fdfe:dcba:9876::1/126
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
            |    - fc00::/7
            |    - fe80::/10
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
            |  udp: false
            |rules:
            |- UID,10101,VLESS
            |- AND,((UID,10102),(NETWORK,UDP),(DST-PORT,443)),REJECT
            |- UID,10102,DPI
            |- MATCH,DIRECT
        """.trimMargin()
    }
}
