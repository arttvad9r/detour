package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsOptionsTest {
    @Test fun `known ids map to servers`() {
        assertEquals("8.8.8.8", DnsOptions.resolve("google", ""))
        assertEquals("https://1.1.1.1/dns-query", DnsOptions.resolve("cloudflare", ""))
        assertEquals("https://dns.adguard-dns.io/dns-query", DnsOptions.resolve("adguard", ""))
    }
    @Test fun `custom is used only for custom id`() {
        assertEquals("9.9.9.9", DnsOptions.resolve("custom", " 9.9.9.9 "))
        assertEquals("8.8.8.8", DnsOptions.resolve("google", "9.9.9.9"))
        assertEquals("8.8.8.8", DnsOptions.resolve("custom", "  "))
    }
    @Test fun `unknown id falls back to default`() {
        assertEquals("8.8.8.8", DnsOptions.resolve("bogus", ""))
    }
}

class SettingsBackupTest {
    private val backup = SettingsBackup.Backup(
        vlessUri = "vless://uuid@host:443?security=reality",
        presetId = "recommended",
        dpiCustomArgs = "-s 5",
        autoConnect = true,
        themeId = "midnight",
        dnsId = "adguard",
        dnsCustom = "",
        routes = mapOf("org.t" to "VPN", "com.y" to "DPI"),
    )

    @Test fun `roundtrip preserves all fields`() {
        val back = SettingsBackup.fromJson(SettingsBackup.toJson(backup))
        assertEquals(backup, back)
    }
    @Test fun `foreign file rejected`() {
        assertNull(SettingsBackup.fromJson("""{"app":"other"}"""))
        assertNull(SettingsBackup.fromJson("not json"))
    }
    @Test fun `missing fields get defaults`() {
        val b = SettingsBackup.fromJson("""{"app":"detour","vless":"vless://x"}""")!!
        assertEquals("recommended", b.presetId)
        assertEquals("lavenda", b.themeId)
        assertTrue(b.routes.isEmpty())
    }
}
