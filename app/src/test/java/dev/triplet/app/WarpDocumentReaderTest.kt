package dev.triplet.app

import dev.triplet.app.core.WarpConfigImporter
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarpDocumentReaderTest {
    @Test fun `reader preserves valid UTF-8 document`() {
        val raw = "proxies:\n  - name: WARP"

        assertEquals(raw, readWarpDocument(ByteArrayInputStream(raw.toByteArray())))
    }

    @Test fun `reader rejects document above importer limit`() {
        val oversized = ByteArray(WarpConfigImporter.MAX_CHARS + 1) { 'a'.code.toByte() }

        assertNull(readWarpDocument(ByteArrayInputStream(oversized)))
    }
}
