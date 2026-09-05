package dev.triplet.app.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiAutoProbeTest {
    @Test fun `built in domain groups have unique ids and targets`() {
        val groups = DpiDomainCatalog.default
        assertTrue(groups.isNotEmpty())
        assertEquals(groups.size, groups.map { it.id }.distinct().size)

        val targets = groups.flatMap { it.targets }
        assertEquals(targets.size, targets.map { it.id }.distinct().size)
    }

    @Test fun `RFC1929 auth request encodes ephemeral credentials`() {
        val request = Socks5Wire.authRequest(ProbeCredentials("user", "pass"))
        assertArrayEquals(
            byteArrayOf(
                0x01, 0x04,
                'u'.code.toByte(), 's'.code.toByte(), 'e'.code.toByte(), 'r'.code.toByte(),
                0x04,
                'p'.code.toByte(), 'a'.code.toByte(), 's'.code.toByte(), 's'.code.toByte(),
            ),
            request,
        )
    }

    @Test fun `SOCKS connect request uses domain address and network byte order port`() {
        val request = Socks5Wire.connectRequest("example.com", 443)
        val host = "example.com".toByteArray(Charsets.US_ASCII)
        val expected = byteArrayOf(0x05, 0x01, 0x00, 0x03, host.size.toByte()) +
            host + byteArrayOf(0x01, 0xbb.toByte())
        assertArrayEquals(expected, request)
    }

    @Test fun `HTTP status parser accepts normal status line only`() {
        assertEquals(204, Socks5Wire.httpStatusCode("HTTP/1.1 204 No Content"))
        assertEquals(404, Socks5Wire.httpStatusCode("HTTP/2 404"))
        assertNull(Socks5Wire.httpStatusCode("ICY 200 OK"))
        assertNull(Socks5Wire.httpStatusCode("HTTP/1.1 nope"))
    }
}
