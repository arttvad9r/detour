package dev.detour.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionMetadataTest {
    @Test fun `metadata parser keeps quota expiry and interval`() {
        val metadata = requireNotNull(
            parseSubscriptionMetadata(
                """{"title":"Premium","updateIntervalHours":12,"uploadBytes":1024,"downloadBytes":2048,"totalBytes":8192,"expireAtUnix":1893456000}""",
            ),
        )

        assertEquals("Premium", metadata.title)
        assertEquals(12, metadata.updateIntervalHours)
        assertEquals(3072L, metadata.usedBytes)
        assertEquals(5120L, metadata.remainingBytes)
        assertEquals(1893456000L, metadata.expireAtUnix)
    }

    @Test fun `empty metadata stays absent`() {
        assertNull(parseSubscriptionMetadata("{}"))
        assertNull(parseSubscriptionMetadata(""))
    }

    @Test fun `invalid metadata values are ignored`() {
        val metadata = requireNotNull(
            parseSubscriptionMetadata(
                """{"title":"Premium","updateIntervalHours":999999,"totalBytes":-1,"expireAtUnix":0}""",
            ),
        )

        assertEquals("Premium", metadata.title)
        assertNull(metadata.updateIntervalHours)
        assertNull(metadata.totalBytes)
        assertNull(metadata.expireAtUnix)
    }
}
