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

    @Test fun `accepts trailing dns root dot and converts to probe target`() {
        val result = DpiDomainInput.parse("sub.example.com.")

        assertTrue(result.isValid)
        assertEquals("sub.example.com", result.targets.single().host)
        assertEquals("custom:sub.example.com", result.targets.single().id)
    }
}
