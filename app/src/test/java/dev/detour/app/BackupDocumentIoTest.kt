package dev.detour.app

import dev.detour.app.core.SettingsBackup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupDocumentIoTest {
    @Test fun `reader preserves valid UTF-8 backup`() {
        val raw = "{\"themeId\":\"dracula\"}"

        assertEquals(raw, readBackupDocument(ByteArrayInputStream(raw.toByteArray())))
    }

    @Test fun `reader rejects backup above size limit`() {
        val oversized = ByteArray(SettingsBackup.MAX_BYTES + 1) { 'a'.code.toByte() }

        assertNull(readBackupDocument(ByteArrayInputStream(oversized)))
    }

    @Test fun `writer emits UTF-8 backup bytes`() {
        val raw = "{\"name\":\"Детур\"}"
        val output = ByteArrayOutputStream()

        writeBackupDocument(output, raw)

        assertEquals(raw, output.toString(Charsets.UTF_8.name()))
    }
}
