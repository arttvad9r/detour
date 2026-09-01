package dev.triplet.app.ui

import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupViewModelStateTest {
    @Test fun `backup mapping preserves persistent user settings only`() {
        val key = VlessKey("id", "Server", "vless://example")
        val settings = TriSettings(
            vlessKeys = VlessKeys(listOf(key), key.id),
            warpProfile = null,
            activeVpn = VpnProfileKind.VLESS,
            preset = DpiPreset.RECOMMENDED,
            dpiCustomArgs = "-d 1 -s 2",
            autoConnect = true,
            themeId = "dracula",
            dnsId = "cloudflare",
            dnsCustom = "",
            routes = mapOf("com.example" to AppRoute.VPN),
            showSystemApps = true,
            sessionStartedAt = 1234L,
        )

        val backup = backupFromSettings(settings)
        assertEquals(settings.vlessKeys, backup.vlessKeys)
        assertEquals(VpnProfileKind.VLESS, backup.activeVpn)
        assertEquals("recommended", backup.presetId)
        assertEquals("-d 1 -s 2", backup.dpiCustomArgs)
        assertTrue(backup.autoConnect)
        assertEquals("dracula", backup.themeId)
        assertEquals("cloudflare", backup.dnsId)
        assertEquals(mapOf("com.example" to "VPN"), backup.routes)
        assertTrue(backup.showSystemApps)
        assertFalse(backup.routes.containsKey("sessionStartedAt"))
    }

    @Test fun `backup operation blocks overlap and clears after completion`() {
        val viewModel = viewModel()

        assertTrue(viewModel.beginExport())
        assertEquals(BackupOperation.EXPORT, viewModel.operation.value)
        assertFalse(viewModel.beginImport())

        viewModel.reportExport(success = true)

        assertNull(viewModel.operation.value)
        assertEquals(BackupStatus.EXPORTED, viewModel.status.value)
        assertTrue(viewModel.beginImport())
        assertEquals(BackupOperation.IMPORT, viewModel.operation.value)
    }

    @Test fun `backup error clears an active operation`() {
        val viewModel = viewModel()

        assertTrue(viewModel.beginImport())
        viewModel.reportError()

        assertNull(viewModel.operation.value)
        assertEquals(BackupStatus.ERROR, viewModel.status.value)
    }

    @Test fun `cancelled operation clears busy state without result feedback`() {
        val viewModel = viewModel()

        assertTrue(viewModel.beginExport())
        viewModel.cancelOperation(BackupOperation.EXPORT)

        assertNull(viewModel.operation.value)
        assertNull(viewModel.status.value)
    }

    private fun viewModel() = BackupViewModel(
        loadSettings = { null },
        readBackupDocument = { null },
        writeBackupDocument = { _, _ -> },
        restoreBackup = {},
        stopTunnelIfRunning = {},
    )
}
