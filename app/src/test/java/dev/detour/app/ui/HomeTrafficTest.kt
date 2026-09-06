package dev.detour.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTrafficTest {
    @Test
    fun parsesTrafficSnapshotAndClampsNegativeValues() {
        val stats = parseHomeTrafficStats(
            """{"uploadBytesPerSecond":1250,"downloadBytesPerSecond":2500000,"uploadedBytes":-1,"downloadedBytes":42000000}""",
        )

        assertEquals(1_250L, stats.uploadBytesPerSecond)
        assertEquals(2_500_000L, stats.downloadBytesPerSecond)
        assertEquals(0L, stats.uploadedBytes)
        assertEquals(42_000_000L, stats.downloadedBytes)
        assertEquals(42_000_000L, stats.totalBytes)
    }

    @Test
    fun malformedTrafficSnapshotFailsClosed() {
        assertEquals(HomeTrafficStats(), parseHomeTrafficStats("not-json"))
        assertEquals(HomeTrafficStats(), parseHomeTrafficStats(""))
    }

    @Test
    fun formatsTrafficCompactly() {
        assertEquals("0 B", formatTrafficBytes(-1))
        assertEquals("999 B", formatTrafficBytes(999))
        assertEquals("1 KB", formatTrafficBytes(1_000))
        assertEquals("1.5 KB", formatTrafficBytes(1_500))
        assertEquals("42 MB", formatTrafficBytes(42_000_000))
        assertEquals("2.5 MB/s", formatTrafficRate(2_500_000))
    }
}
