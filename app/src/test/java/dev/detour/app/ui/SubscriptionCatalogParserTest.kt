package dev.detour.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionCatalogParserTest {
    @Test
    fun `supported protocol names are canonicalized`() {
        assertEquals("vless", canonicalSubscriptionCatalogType(" VLESS "))
        assertEquals("trojan", canonicalSubscriptionCatalogType("Trojan"))
        assertEquals("hysteria2", canonicalSubscriptionCatalogType("HYSTERIA2"))
        assertNull(canonicalSubscriptionCatalogType("socks5"))
        assertNull(canonicalSubscriptionCatalogType("wireguard"))
    }

    @Test
    fun `catalog keeps allowed protocols and removes blocked and duplicate nodes`() {
        val raw = """
            {
              "nodes": [
                {"name":"VLESS A","type":"vless"},
                {"name":"Trojan B","type":"TROJAN"},
                {"name":"SOCKS C","type":"socks5"},
                {"name":"VLESS A","type":"vmess"},
                {"name":"TUIC D","type":"tuic"}
              ]
            }
        """.trimIndent()

        assertEquals(
            listOf(
                SubscriptionCatalogNode("VLESS A", "vless"),
                SubscriptionCatalogNode("Trojan B", "trojan"),
                SubscriptionCatalogNode("TUIC D", "tuic"),
            ),
            parseSubscriptionCatalog(raw, maxJsonChars = 32 * 1024, maxNodes = 256),
        )
    }

    @Test
    fun `catalog rejects malformed or oversized payloads`() {
        assertEquals(emptyList<SubscriptionCatalogNode>(), parseSubscriptionCatalog("not-json", 1024, 256))
        assertEquals(emptyList<SubscriptionCatalogNode>(), parseSubscriptionCatalog("{}", 1, 256))
        assertEquals(emptyList<SubscriptionCatalogNode>(), parseSubscriptionCatalog("{}", 1024, 0))
    }
}
