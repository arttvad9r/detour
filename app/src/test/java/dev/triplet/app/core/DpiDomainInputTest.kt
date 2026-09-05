package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiDomainInputTest {
    @Test fun `parses normalizes and deduplicates domain hosts`() {
        val result = DpiDomainInput.parse(" Example.COM\nпример.рф,example.com ")

        assertTrue(result.isValid)
        assertEquals(
            listOf("example.com", "xn--e1afmkfd.xn--p1ai"),
            result.targets.map { it.host },
        )
    }

    @Test fun `rejects urls ports paths wildcards and single label hosts`() {
        val result = DpiDomainInput.parse(
            "https://example.com example.com:443 example.com/path *.example.com localhost",
        )

        assertFalse(result.isValid)
        assertEquals(5, result.invalid.size)
        assertTrue(result.targets.isEmpty())
    }

    @Test fun `rejects ipv4 literals but keeps valid domain targets`() {
        val result = DpiDomainInput.parse("1.2.3.4 192.168.001.010 example.com")

        assertFalse(result.isValid)
        assertEquals(listOf("1.2.3.4", "192.168.001.010"), result.invalid)
        assertEquals(listOf("example.com"), result.targets.map { it.host })
    }

    @Test fun `accepts trailing dns root dot and converts to probe target`() {
        val result = DpiDomainInput.parse("sub.example.com.")

        assertTrue(result.isValid)
        assertEquals("sub.example.com", result.targets.single().host)
        assertEquals("custom:sub.example.com", result.targets.single().id)
    }
}
