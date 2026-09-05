package dev.triplet.app.core

import dev.triplet.app.data.RoutesMapping
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DpiAutoPersistenceTest {
    @Test fun `auto preset resolves only trusted catalog ids`() {
        val expected = requireNotNull(DpiStrategyCatalog.byId("split-sni")).args
        assertEquals(
            expected,
            DpiArgs.resolve(DpiPreset.AUTO, customRaw = "-d 999", autoCandidateId = "split-sni"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown auto strategy fails closed`() {
        DpiArgs.resolve(DpiPreset.AUTO, customRaw = "", autoCandidateId = "not-in-catalog")
    }

    @Test fun `routes mapping preserves auto candidate independently from custom args`() {
        val settings = RoutesMapping.toSettings(
            mapOf(
                "dpi_preset" to "auto",
                "dpi_custom_args" to "-d 7",
                "dpi_auto_candidate" to "split-sni",
                "dns_id" to "google",
            ),
        )

        assertEquals(DpiPreset.AUTO, settings.preset)
        assertEquals("-d 7", settings.dpiCustomArgs)
        assertEquals("split-sni", settings.dpiAutoCandidateId)
    }

    @Test fun `backup v4 round trips trusted auto strategy`() {
        val original = SettingsBackup.Backup(
            presetId = DpiPreset.AUTO.id,
            dpiCustomArgs = "-d 7",
            dpiAutoCandidateId = "split-disorder-sni",
        )

        val restored = SettingsBackup.fromJson(SettingsBackup.toJson(original))

        requireNotNull(restored)
        assertEquals(DpiPreset.AUTO.id, restored.presetId)
        assertEquals("-d 7", restored.dpiCustomArgs)
        assertEquals("split-disorder-sni", restored.dpiAutoCandidateId)
    }

    @Test fun `backup rejects unknown automatic strategy`() {
        val raw = JSONObject(SettingsBackup.toJson(SettingsBackup.Backup())).apply {
            put("preset", "auto")
            put("autoCandidate", "unknown")
        }.toString()

        assertNull(SettingsBackup.fromJson(raw))
    }

    @Test fun `legacy v3 cannot activate auto without auto strategy schema`() {
        val raw = JSONObject(SettingsBackup.toJson(SettingsBackup.Backup())).apply {
            put("v", 3)
            put("preset", "auto")
            remove("autoCandidate")
        }.toString()

        assertNull(SettingsBackup.fromJson(raw))
    }
}
