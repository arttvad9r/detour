package dev.triplet.app.core

import org.junit.Assert.assertTrue
import org.junit.Test

class WarpProfileValidationTest {
    private fun proxy() = WarpProxy(
        name = "WARP",
        server = "162.159.195.1",
        port = 2408,
        ip = "172.16.0.2",
        ipv6 = "2606:4700:110::2",
        privateKey = "private",
        publicKey = "public",
        reserved = listOf(1, 2, 3),
        allowedIps = listOf("0.0.0.0/0", "::/0"),
        dns = listOf("1.1.1.1"),
        amnezia = AmneziaWgOptions(jc = 1, i1 = "<b 0x1234>"),
    )

    @Test fun `accepts valid IPv4 and IPv6 literals`() {
        assertTrue(runCatching { validateWarpProxy(proxy()) }.isSuccess)
    }

    @Test fun `rejects control characters in rendered warp scalars`() {
        val variants = listOf(
            proxy().copy(server = "bad\nhost"),
            proxy().copy(allowedIps = listOf("0.0.0.0/0\r")),
            proxy().copy(amnezia = proxy().amnezia.copy(i1 = "bad\u0000value")),
        )

        variants.forEach { candidate ->
            assertTrue(runCatching { validateWarpProxy(candidate) }.isFailure)
        }
    }

    @Test fun `rejects hostnames and malformed addresses where literals are required`() {
        val variants = listOf(
            proxy().copy(ip = "not-an-ip"),
            proxy().copy(ip = "example.com"),
            proxy().copy(ip = "999.1.1.1"),
            proxy().copy(dns = listOf("dns.example.com")),
            proxy().copy(allowedIps = listOf("10.0.0.0/99")),
            proxy().copy(amnezia = proxy().amnezia.copy(jmin = 80, jmax = 40)),
        )
        variants.forEach { candidate ->
            assertTrue(runCatching { validateWarpProxy(candidate) }.isFailure)
        }
    }
}
