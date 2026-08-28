package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarpConfigImporterTest {
    private val yaml = """
        amnezia-common: &amnezia-common
          jc: 4
          jmin: 40
          jmax: 70
          s1: 0
          s2: 0
          h1: 1
          h2: 2
          h3: 3
          h4: 4

        warp-common: &warp-common
          type: wireguard
          ip: 172.16.0.2
          ipv6: 2606:4700:110::2
          private-key: private
          public-key: public
          reserved: [15, 229, 28]
          allowed-ips: ['0.0.0.0/0', '::/0']
          persistent-keepalive: 25
          udp: true
          mtu: 1280
          remote-dns-resolve: true
          dns: [1.1.1.1, 1.0.0.1]
          amnezia-wg-option:
            <<: *amnezia-common
            i1: '<b 0x1234>'

        masque-common: &masque-common
          type: masque
          private-key: ignored

        proxies:
          - name: Netherlands
            <<: *warp-common
            server: nl.example.net
            port: 4500
          - name: Netherlands MASQUE
            <<: *masque-common
            server: nl.example.net
            port: 443
    """.trimIndent()

    @Test fun `imports merged AmneziaWG proxy and ignores MASQUE`() {
        val result = WarpConfigImporter.parse(yaml)
        assertTrue(result is WarpImportResult.Ok)
        val profile = (result as WarpImportResult.Ok).profile
        assertEquals(1, profile.proxies.size)
        val proxy = profile.proxies.single()
        assertEquals("Netherlands", proxy.name)
        assertEquals("nl.example.net", proxy.server)
        assertEquals(4500, proxy.port)
        assertEquals("172.16.0.2", proxy.ip)
        assertEquals(listOf(15, 229, 28), proxy.reserved)
        assertEquals(listOf("0.0.0.0/0", "::/0"), proxy.allowedIps)
        assertEquals(25, proxy.persistentKeepalive)
        assertEquals(4, proxy.amnezia.jc)
        assertEquals(40, proxy.amnezia.jmin)
        assertEquals("<b 0x1234>", proxy.amnezia.i1)
    }

    @Test fun `prefers Warp Generator starred direct endpoints over geo relays`() {
        val config = """
            common: &common
              type: wireguard
              ip: 172.16.0.2
              private-key: private
              public-key: public
              allowed-ips: ['0.0.0.0/0']
              amnezia-wg-option:
                jc: 4
                jmin: 40
                jmax: 70
                s1: 0
                s2: 0
                h1: 1
                h2: 2
                h3: 3
                h4: 4
                i1: '<b 0x1234>'
            proxies:
              - name: "[🌍] geo relay"
                <<: *common
                server: relay.example.net
                port: 4500
              - name: "[⭐] direct one"
                <<: *common
                server: 162.159.195.1
                port: 500
              - name: "[⭐] direct two"
                <<: *common
                server: engage.cloudflareclient.com
                port: 2408
        """.trimIndent()

        val profile = (WarpConfigImporter.parse(config) as WarpImportResult.Ok).profile
        assertEquals(2, profile.proxies.size)
        assertTrue(profile.proxies.all { it.name.contains("⭐") })
    }

    @Test fun `imports native AmneziaWG conf`() {
        val conf = """
            [Interface]
            PrivateKey = private
            Address = 172.16.0.2, 2606:4700:110::2
            DNS = 1.1.1.1, 1.0.0.1
            MTU = 1280
            S1 = 0
            S2 = 0
            Jc = 4
            Jmin = 40
            Jmax = 70
            H1 = 1
            H2 = 2
            H3 = 3
            H4 = 4
            I1 = <b 0x1234>

            [Peer]
            PublicKey = public
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = 162.159.195.1:500
            PersistentKeepalive = 25
        """.trimIndent()

        val result = WarpConfigImporter.parse(conf)
        assertTrue(result is WarpImportResult.Ok)
        val proxy = (result as WarpImportResult.Ok).profile.proxies.single()
        assertEquals("162.159.195.1", proxy.server)
        assertEquals(500, proxy.port)
        assertEquals("172.16.0.2", proxy.ip)
        assertEquals("2606:4700:110::2", proxy.ipv6)
        assertEquals(emptyList<Int>(), proxy.reserved)
        assertEquals(listOf("0.0.0.0/0", "::/0"), proxy.allowedIps)
        assertEquals(listOf("1.1.1.1", "1.0.0.1"), proxy.dns)
        assertEquals(1280, proxy.mtu)
        assertEquals(25, proxy.persistentKeepalive)
        assertEquals(4, proxy.amnezia.jc)
        assertEquals("<b 0x1234>", proxy.amnezia.i1)
    }

    @Test fun `native conf supports bracketed ipv6 endpoint`() {
        val conf = """
            [Interface]
            PrivateKey = private
            Address = 172.16.0.2
            Jc = 1

            [Peer]
            PublicKey = public
            AllowedIPs = 0.0.0.0/0
            Endpoint = [2606:4700:d0::a29f:c001]:2408
        """.trimIndent()

        val proxy = ((WarpConfigImporter.parse(conf) as WarpImportResult.Ok).profile.proxies.single())
        assertEquals("2606:4700:d0::a29f:c001", proxy.server)
        assertEquals(2408, proxy.port)
    }

    @Test fun `regular generator config with more than fifty aliases is accepted`() {
        val proxies = (1..135).joinToString("\n") { n ->
            """
              - name: endpoint-$n
                <<: *warp-common
                server: endpoint-$n.example.net
                port: ${listOf(2408, 1701, 4500, 500)[n % 4]}
            """.trimIndent()
        }
        val large = """
            warp-common: &warp-common
              type: wireguard
              ip: 172.16.0.2
              private-key: private
              public-key: public
              reserved: [109, 84, 209]
              allowed-ips: ['0.0.0.0/0']
              udp: true
              mtu: 1280
              amnezia-wg-option:
                jc: 4
                jmin: 40
                jmax: 70
                s1: 0
                s2: 0
                h1: 1
                h2: 2
                h3: 4
                h4: 3
                i1: '<b 0x1234>'
            proxies:
            $proxies
        """.trimIndent()

        val result = WarpConfigImporter.parse(large)
        assertTrue(result is WarpImportResult.Ok)
        assertEquals(128, (result as WarpImportResult.Ok).profile.proxies.size)
    }

    @Test fun `plain wireguard without AmneziaWG is not accepted`() {
        val result = WarpConfigImporter.parse(
            """
            proxies:
              - name: plain
                type: wireguard
                server: wg.example.net
                port: 2408
            """.trimIndent(),
        )
        assertEquals(WarpImportResult.NoCompatibleProxies, result)
    }

    @Test fun `invalid and oversized config is rejected`() {
        assertEquals(WarpImportResult.Invalid, WarpConfigImporter.parse("[broken"))
        assertEquals(
            WarpImportResult.Invalid,
            WarpConfigImporter.parse("x".repeat(WarpConfigImporter.MAX_CHARS + 1)),
        )
    }
}
