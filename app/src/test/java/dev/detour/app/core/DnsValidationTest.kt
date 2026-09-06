package dev.detour.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsValidationTest {
    @Test fun `validator accepts IP literals and HTTPS DoH without hostname lookup`() {
        assertTrue(DnsOptions.isValid("9.9.9.9"))
        assertTrue(DnsOptions.isValid("2001:4860:4860::8888"))
        assertTrue(DnsOptions.isValid("https://dns.example/dns-query"))
        assertTrue(DnsOptions.isValid("https://1.1.1.1/dns-query"))
        assertFalse(DnsOptions.isValid("dns.example"))
        assertFalse(DnsOptions.isValid("http://dns.example/dns-query"))
        assertFalse(DnsOptions.isValid("https://dns.example:0/dns-query"))
        assertFalse(DnsOptions.isValid("9.9.9.9\n#bad"))
    }

    @Test fun `blank selection keeps historical google default`() {
        assertEquals(DnsOptions.DEFAULT_SERVER, DnsOptions.resolve("", ""))
    }

    @Test fun `unknown selection and invalid custom fail closed`() {
        val unknown = runCatching { DnsOptions.resolve("removed-provider", "") }.exceptionOrNull()
        val invalidCustom = runCatching {
            DnsOptions.resolve(DnsOptions.CUSTOM, "not-a-resolver")
        }.exceptionOrNull()

        assertTrue(unknown is IllegalArgumentException)
        assertTrue(invalidCustom is IllegalArgumentException)
    }

    @Test fun `hostname DoH gets bootstrap but IP DoH does not`() {
        assertEquals("8.8.8.8", DnsOptions.bootstrapServer("https://dns.adguard-dns.io/dns-query"))
        assertNull(DnsOptions.bootstrapServer("https://1.1.1.1/dns-query"))
        assertNull(DnsOptions.bootstrapServer("9.9.9.9"))
    }

    @Test fun `config emits bootstrap resolver only when DNS server needs it`() {
        val hostnameDoh = ConfigGenerator.build(input("https://dns.adguard-dns.io/dns-query"))
        assertTrue(hostnameDoh.contains("default-nameserver:\n    - 8.8.8.8"))
        assertTrue(hostnameDoh.contains("nameserver:\n    - \"https://dns.adguard-dns.io/dns-query\""))

        val ipDoh = ConfigGenerator.build(input("https://1.1.1.1/dns-query"))
        assertFalse(ipDoh.contains("default-nameserver:"))
    }

    private fun input(nameserver: String) = RoutingInput(
        tunFd = 7,
        apiLevel = 36,
        vpn = null,
        vpnApps = emptySet(),
        vpnUids = emptyMap(),
        dpiApps = emptySet(),
        nameserver = nameserver,
    )
}
