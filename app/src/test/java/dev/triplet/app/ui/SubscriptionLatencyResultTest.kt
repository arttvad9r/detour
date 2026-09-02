package dev.triplet.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionLatencyResultTest {
    @Test
    fun `latency parser keeps successful and failed tested nodes distinct`() {
        val result = parseSubscriptionLatencyResult(
            """
            {
              "nodes": [
                {"name":"Finland - 1","delayMs":83},
                {"name":"Germany - 3"},
                {"name":"Too slow","delayMs":70000}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(setOf("Finland - 1", "Germany - 3", "Too slow"), result.testedNames)
        assertEquals(83, result.delayByName["Finland - 1"])
        assertFalse(result.delayByName.containsKey("Germany - 3"))
        assertFalse(result.delayByName.containsKey("Too slow"))
    }

    @Test
    fun `latency parser rejects unsafe names and malformed payloads`() {
        val result = parseSubscriptionLatencyResult(
            """{"nodes":[{"name":"bad\u0001name","delayMs":10},{"name":"ok","delayMs":42}]}""",
        )
        assertEquals(setOf("ok"), result.testedNames)
        assertEquals(mapOf("ok" to 42), result.delayByName)

        assertTrue(parseSubscriptionLatencyResult("").testedNames.isEmpty())
        assertTrue(parseSubscriptionLatencyResult("not-json").delayByName.isEmpty())
    }
}
