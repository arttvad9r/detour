package dev.triplet.app.vpn

import dev.triplet.app.core.ProbeCredentials
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckTest {
    @Test fun `health probe retries each endpoint and succeeds on fallback`() {
        val seen = mutableListOf<String>()

        assertTrue(
            HealthCheck.retry(
                listOf("first", "second"), attempts = 2,
            ) { endpoint ->
                seen += endpoint
                endpoint == "second"
            },
        )

        assertEquals(listOf("first", "first", "second"), seen)
    }

    @Test fun `health probe stops when interrupted`() {
        Thread.currentThread().interrupt()
        try {
            assertTrue(!HealthCheck.retry(listOf("first"), attempts = 2) { true })
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun `connect request authenticates only to loopback proxy`() {
        val request = HealthCheck.proxyConnectRequest(
            "example.com", 443, ProbeCredentials("probe-user", "probe-secret"),
        )

        assertTrue(request.startsWith("CONNECT example.com:443 HTTP/1.1\r\n"))
        val encoded = request.lineSequence()
            .first { it.startsWith("Proxy-Authorization: Basic ") }
            .substringAfter("Proxy-Authorization: Basic ")
            .trim()
        assertEquals(
            "probe-user:probe-secret",
            String(Base64.getDecoder().decode(encoded), StandardCharsets.ISO_8859_1),
        )
    }

    @Test fun `status parser rejects malformed responses`() {
        assertEquals(200, HealthCheck.statusCode("HTTP/1.1 200 Connection established"))
        assertEquals(204, HealthCheck.statusCode("HTTP/1.1 204 No Content"))
        assertEquals(null, HealthCheck.statusCode("garbage"))
        assertEquals(null, HealthCheck.statusCode("HTTP/1.1 nope"))
    }
}
