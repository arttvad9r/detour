package dev.triplet.app.vpn

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
}
