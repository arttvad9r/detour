package dev.detour.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeBackupTest {
    @Test fun `new palette ids survive backup roundtrip`() {
        dev.detour.app.ui.AppTheme.entries.map { it.id }.forEach { id ->
            val restored = SettingsBackup.fromJson(
                SettingsBackup.toJson(SettingsBackup.Backup(themeId = id)),
            )
            assertNotNull(restored)
            assertEquals(id, restored?.themeId)
        }
    }

    @Test fun `legacy palette ids are still accepted`() {
        listOf("lavenda", "ocean", "midnight", "graphite").forEach { id ->
            val restored = SettingsBackup.fromJson(
                SettingsBackup.toJson(SettingsBackup.Backup(themeId = id)),
            )
            assertNotNull(restored)
            assertEquals(id, restored?.themeId)
        }
    }
}
