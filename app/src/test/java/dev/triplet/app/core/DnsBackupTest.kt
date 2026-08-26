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
    @Test fun `validator accepts ip and https doh only`() {
        assertTrue(DnsOptions.isValid("9.9.9.9"))
        assertTrue(DnsOptions.isValid("https://dns.example/dns-query"))
        assertTrue(!DnsOptions.isValid("http://dns.example/dns-query"))
        assertTrue(!DnsOptions.isValid("9.9.9.9\n#bad"))
    }
}

class SettingsBackupTest {
    private val backup = SettingsBackup.Backup(
        vlessUri = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443?type=tcp&security=reality&fp=chrome&sni=example.com&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179&flow=xtls-rprx-vision",
        presetId = "recommended",
        dpiCustomArgs = "-s 5",
        autoConnect = true,
        themeId = "midnight",
        dnsId = "adguard",
        dnsCustom = "",
        routes = mapOf("org.t" to "VPN", "com.y" to "DPI"),
        vlessKeys = VlessKeys(
            listOf(VlessKey("primary", "Primary", "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443?type=tcp&security=reality&fp=chrome&sni=example.com&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179&flow=xtls-rprx-vision")),
            "primary",
        ),
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
        val b = SettingsBackup.fromJson("""{"app":"detour","vless":""}""")!!
        assertEquals("recommended", b.presetId)
        assertEquals("lavenda", b.themeId)
        assertTrue(b.routes.isEmpty())
    }

    @Test fun `v2 preserves multiple keys and active id`() {
        val keys = VlessKeys(
            listOf(
                VlessKey("a", "A", backup.vlessUri),
                VlessKey("b", "B", backup.vlessUri),
                VlessKey("c", "C", backup.vlessUri),
            ), "b",
        )
        val restored = SettingsBackup.fromJson(SettingsBackup.toJson(backup.copy(vlessKeys = keys)))!!
        assertEquals(2, SettingsBackup.VERSION)
        assertEquals(listOf("a", "b", "c"), restored.vlessKeys.items.map { it.id })
        assertEquals("b", restored.vlessKeys.activeId)
    }

    @Test fun `v1 migrates one key and unknown versions reject`() {
        val old = """{"v":1,"app":"detour","vless":"${backup.vlessUri}"}"""
        assertEquals(1, SettingsBackup.fromJson(old)!!.vlessKeys.items.size)
        assertNull(SettingsBackup.fromJson("""{"v":99,"app":"detour"}"""))
        assertNull(SettingsBackup.fromJson("""{"v":2,"app":"detour","vlessKeys":{},"routes":{"x":"BROKEN"}}"""))
    }

    @Test fun `empty persisted defaults remain importable`() {
        val json = """{"v":2,"app":"detour","vlessKeys":{"activeId":null,"items":[]},"preset":"recommended","customArgs":"","autoConnect":false,"theme":"","dns":"","dnsCustom":"","routes":{}}"""
        val restored = SettingsBackup.fromJson(json)!!
        assertEquals("lavenda", restored.themeId)
        assertEquals("google", restored.dnsId)
    }
}
