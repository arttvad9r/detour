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
        assertEquals(4, proxy.amnezia.jc)
        assertEquals(40, proxy.amnezia.jmin)
        assertEquals("<b 0x1234>", proxy.amnezia.i1)
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

    @Test fun `invalid and oversized yaml is rejected`() {
        assertEquals(WarpImportResult.Invalid, WarpConfigImporter.parse("[broken"))
        assertEquals(
            WarpImportResult.Invalid,
            WarpConfigImporter.parse("x".repeat(WarpConfigImporter.MAX_CHARS + 1)),
        )
    }
}
