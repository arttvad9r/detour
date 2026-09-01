package dev.triplet.app.ui

import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.SettingsBackup
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.TriSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupExportSnapshotTest {
    @Test fun `export loads latest committed snapshot on demand`() = runBlocking {
        var committed = TriSettings(
            vlessKeys = VlessKeys(emptyList(), null),
            warpProfile = null,
            activeVpn = VpnProfileKind.VLESS,
            preset = DpiPreset.RECOMMENDED,
            dpiCustomArgs = "",
            autoConnect = false,
            themeId = "catppuccin_latte",
            dnsId = "google",
            dnsCustom = "",
            routes = emptyMap(),
            showSystemApps = false,
            sessionStartedAt = null,
        )
        val viewModel = BackupViewModel(
            loadSettings = { committed },
            readBackupDocument = { null },
            writeBackupDocument = { _, _ -> },
            restoreBackup = {},
            stopTunnelIfRunning = {},
        )

        committed = committed.copy(themeId = "dracula", showSystemApps = true)
        val exported = SettingsBackup.fromJson(viewModel.exportJson()!!)!!

        assertEquals("dracula", exported.themeId)
        assertTrue(exported.showSystemApps)
    }
}
