package dev.triplet.app.ui

import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
