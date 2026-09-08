package dev.detour.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmneziaWg3ImporterTest {

    @Test fun `imports native AmneziaWG 3_1 config without dropping v3 fields`() {
        val result = WarpConfigImporter.parse(nativeAwg3())
        assertTrue(result is WarpImportResult.Ok)
        val proxy = (result as WarpImportResult.Ok).profile.proxies.single()

        assertEquals(3, proxy.amnezia.version)
        assertEquals(12, proxy.amnezia.s3)
        assertEquals(13, proxy.amnezia.s4)
        assertEquals("100-200", proxy.amnezia.h1)
        assertEquals("header-key", proxy.amnezia.headerProtectionKey)
        assertEquals("10-100", proxy.amnezia.contentPaddingAddition)
        assertEquals("100-120", proxy.amnezia.rekeyAfterTime)
        assertEquals("3-7", proxy.amnezia.rekeyTimeout)
        assertEquals("150-180", proxy.amnezia.rejectAfterTime)
        assertEquals("5-15", proxy.amnezia.keepaliveTimeout)
        assertEquals("15-20", proxy.amnezia.maxHandshakeAttempts)
        assertEquals(true, proxy.amnezia.randomTrailers)
        assertEquals(true, proxy.amnezia.disableCookies)
        assertEquals("peer-psk", proxy.preSharedKey)
        assertEquals(30, proxy.persistentKeepalive)
    }

    @Test fun `imports Mihomo AmneziaWG 3 config with explicit version and psk`() {
        val yaml = """
            proxies:
              - name: AWG3
                type: wireguard
                server: 203.0.113.8
                port: 443
                ip: 10.8.1.2
                private-key: private
                public-key: public
                pre-shared-key: psk
                allowed-ips: ['0.0.0.0/0']
                persistent-keepalive: '25-35'
                amnezia-wg-option:
                  version: 3
                  jc: 5
                  jmin: 40
                  jmax: 90
                  s1: 10
                  s2: 11
                  s3: 12
                  s4: 13
                  h1: '100-200'
                  h2: 2
                  h3: 3
                  h4: 4
                  i1: '<b 0x1234>'
                  header-protection-key: header-key
                  content-padding-addition: '10-100'
                  rekey-after-time: '100-120'
                  rekey-timeout: '3-7'
                  reject-after-time: '150-180'
                  keepalive-timeout: '5-15'
                  max-handshake-attempts: '15-20'
                  random-trailers: true
                  disable-cookies: true
        """.trimIndent()

        val proxy = ((WarpConfigImporter.parse(yaml) as WarpImportResult.Ok).profile.proxies.single())
        assertEquals(3, proxy.amnezia.version)
        assertEquals("psk", proxy.preSharedKey)
        assertEquals(30, proxy.persistentKeepalive)
        assertEquals("100-200", proxy.amnezia.h1)
        assertEquals(true, proxy.amnezia.randomTrailers)
    }

    companion object {
        fun nativeAwg3(): String = """
            [Interface]
            PrivateKey = private
            Address = 10.8.1.2/32
            DNS = 1.1.1.1
            MTU = 1420
            Jc = 5
            Jmin = 40
            Jmax = 90
            S1 = 10
            S2 = 11
            S3 = 12
            S4 = 13
            H1 = 100-200
            H2 = 2
            H3 = 3
            H4 = 4
            I1 = <b 0x1234>
            HeaderProtectionKey = header-key
            ContentPaddingAddition = 10-100
            RekeyAfterTime = 100-120
            RekeyTimeout = 3-7
            RejectAfterTime = 150-180
            KeepaliveTimeout = 5-15
            MaxHandshakeAttempts = 15-20
            RandomTrailers = on
            DisableCookies = on

            [Peer]
            PublicKey = peer-public
            PresharedKey = peer-psk
            AllowedIPs = 0.0.0.0/0
            Endpoint = 203.0.113.8:443
            PersistentKeepalive = 25-35
        """.trimIndent()
    }
}
