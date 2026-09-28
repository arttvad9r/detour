package dev.detour.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TunnelTrafficTest {
    @Test
    fun `parses engine counters and clamps negatives`() {
        val stats = parseTunnelTrafficStats(
            """{"uploadBytesPerSecond":12,"downloadBytesPerSecond":-5,"uploadedBytes":100,"downloadedBytes":900}""",
        )!!
        assertEquals(12L, stats.uploadBytesPerSecond)
        assertEquals(0L, stats.downloadBytesPerSecond)
        assertEquals(1000L, stats.totalBytes)
    }

    @Test
    fun `missing engine counters mean no traffic line`() {
        assertNull(parseTunnelTrafficStats(""))
        assertNull(parseTunnelTrafficStats("not json"))
    }
}
