package dev.detour.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the structural features used by Warp Generator's ClashX/ultimate config. */
class WarpRealShapeTest {
    @Test fun `nested anchors scalar aliases and mixed transports import cleanly`() {
        val yaml = """
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
            i1-default: &i1-default <b 0xce0000000108>
            warp-common: &warp-common
              type: wireguard
              ip: 172.16.0.2
              ipv6: 2606:4700:110::2
              private-key: private
              public-key: public
              reserved: [15, 229, 28]
              allowed-ips: ['0.0.0.0/0', '::/0']
              mtu: 1280
              amnezia-wg-option: &amnezia-base
                <<: *amnezia-common
                i1: *i1-default
            warp-masque: &warp-masque
              type: masque
              private-key: ignored
              public-key: ignored
              ip: 172.16.0.2
            proxies:
              - name: '[🌍] 🇳🇱 Netherlands'
                <<: *warp-common
                server: nl.example.net
                port: 4500
              - name: '[🌍 MQ] 🇳🇱 Netherlands'
                <<: *warp-masque
                server: nl.example.net
                port: 443
        """.trimIndent()

        val result = WarpConfigImporter.parse(yaml)
        assertTrue(result is WarpImportResult.Ok)
        val proxy = (result as WarpImportResult.Ok).profile.proxies.single()
        assertEquals("<b 0xce0000000108>", proxy.amnezia.i1)
        assertEquals(4, proxy.amnezia.jc)
        assertEquals("nl.example.net", proxy.server)
        assertEquals(4500, proxy.port)
    }
}
