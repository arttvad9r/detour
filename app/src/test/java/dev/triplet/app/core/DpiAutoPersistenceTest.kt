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
        assertNull(settings.dpiAutoDomainPlan)
    }

    @Test fun `routes mapping restores validated structured domain plan`() {
        val plan = DpiAutoDomainPlan.of(
            mapOf(
                "youtube.com" to "split-sni",
                "discord.com" to "disorder-1",
            ),
        )
        val settings = RoutesMapping.toSettings(
            mapOf(
                "dpi_preset" to "auto",
                "dpi_auto_domain_plan" to plan.toStored(),
            ),
        )

        assertEquals(DpiPreset.AUTO, settings.preset)
        assertEquals(plan, settings.dpiAutoDomainPlan)
        assertEquals("", settings.dpiAutoCandidateId)
    }

    @Test fun `routes mapping drops corrupt structured domain plan`() {
        val settings = RoutesMapping.toSettings(
            mapOf(
                "dpi_preset" to "auto",
                "dpi_auto_domain_plan" to "{broken",
            ),
        )

        assertNull(settings.dpiAutoDomainPlan)
        assertEquals("", settings.dpiAutoCandidateId)
    }

    @Test fun `backup v5 round trips trusted global auto strategy`() {
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
        assertNull(restored.dpiAutoDomainPlan)
    }

    @Test fun `backup v5 round trips structured domain auto plan`() {
        val plan = DpiAutoDomainPlan.of(
            mapOf(
                "youtube.com" to "split-sni",
                "discord.com" to "disorder-1",
            ),
        )
        val original = SettingsBackup.Backup(
            presetId = DpiPreset.AUTO.id,
            dpiCustomArgs = "-d 7",
            dpiAutoDomainPlan = plan,
        )

        val restored = SettingsBackup.fromJson(SettingsBackup.toJson(original))

        requireNotNull(restored)
        assertEquals(DpiPreset.AUTO.id, restored.presetId)
        assertEquals("", restored.dpiAutoCandidateId)
        assertEquals(plan, restored.dpiAutoDomainPlan)
    }

    @Test fun `legacy v4 global auto remains importable`() {
        val raw = JSONObject(SettingsBackup.toJson(SettingsBackup.Backup())).apply {
            put("v", 4)
            put("preset", DpiPreset.AUTO.id)
            put("autoCandidate", "split-sni")
            remove("autoDomainPlan")
        }.toString()

        val restored = SettingsBackup.fromJson(raw)

        requireNotNull(restored)
        assertEquals("split-sni", restored.dpiAutoCandidateId)
        assertNull(restored.dpiAutoDomainPlan)
    }

    @Test fun `backup rejects unknown automatic strategy`() {
        val raw = JSONObject(SettingsBackup.toJson(SettingsBackup.Backup())).apply {
            put("preset", "auto")
            put("autoCandidate", "unknown")
        }.toString()

        assertNull(SettingsBackup.fromJson(raw))
    }

    @Test fun `backup rejects conflicting global and domain auto`() {
        val raw = JSONObject(SettingsBackup.toJson(SettingsBackup.Backup())).apply {
            put("preset", DpiPreset.AUTO.id)
            put("autoCandidate", "split-sni")
            put(
                "autoDomainPlan",
                JSONObject(DpiAutoDomainPlan.of(mapOf("youtube.com" to "split-sni")).toStored()),
            )
        }.toString()

        assertNull(SettingsBackup.fromJson(raw))
    }

    @Test fun `legacy v3 cannot activate auto without auto strategy schema`() {
        val raw = JSONObject(SettingsBackup.toJson(SettingsBackup.Backup())).apply {
            put("v", 3)
            put("preset", "auto")
            remove("autoCandidate")
            remove("autoDomainPlan")
        }.toString()

        assertNull(SettingsBackup.fromJson(raw))
    }
}
