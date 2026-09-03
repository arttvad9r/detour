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
                {
                  "name":"Germany - 3",
                  "errorClass":"reality",
                  "errorText":"REALITY handshake failed: connection closed"
                },
                {"name":"Too slow","delayMs":70000}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(setOf("Finland - 1", "Germany - 3", "Too slow"), result.testedNames)
        assertEquals(83, result.delayByName["Finland - 1"])
        assertFalse(result.delayByName.containsKey("Germany - 3"))
        assertFalse(result.delayByName.containsKey("Too slow"))
        assertEquals(
            SubscriptionLatencyError(
                errorClass = "reality",
                errorText = "REALITY handshake failed: connection closed",
            ),
            result.errorByName["Germany - 3"],
        )
        assertFalse(result.errorByName.containsKey("Finland - 1"))
        assertFalse(result.errorByName.containsKey("Too slow"))
    }

    @Test
    fun `latency parser rejects unsafe names diagnostics and malformed payloads`() {
        val result = parseSubscriptionLatencyResult(
            """
            {
              "nodes": [
                {"name":"bad\u0001name","delayMs":10},
                {"name":"ok","delayMs":42},
                {"name":"bad diagnostic","errorClass":"tls","errorText":"bad\u0001text"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(setOf("ok", "bad diagnostic"), result.testedNames)
        assertEquals(mapOf("ok" to 42), result.delayByName)
        assertTrue(result.errorByName.isEmpty())

        assertTrue(parseSubscriptionLatencyResult("").testedNames.isEmpty())
        assertTrue(parseSubscriptionLatencyResult("not-json").delayByName.isEmpty())
        assertTrue(parseSubscriptionLatencyResult("not-json").errorByName.isEmpty())
    }
}
