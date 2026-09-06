package dev.detour.app.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultiHopBackupTest {
    private val entryUri =
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@entry.example.com:443" +
            "?type=tcp&security=reality&fp=chrome&sni=translate.yandex.com" +
            "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
            "&flow=xtls-rprx-vision#Entry"
    private val exitUri = entryUri
        .replace("entry.example.com", "exit.example.com")
        .replace("#Entry", "#Exit")

    private fun backupWithVlessEntry(): SettingsBackup.Backup {
        val keys = VlessKeys(
            items = listOf(
                VlessKey("entry", "Entry", entryUri),
                VlessKey("exit", "Exit", exitUri),
            ),
            activeId = "exit",
        )
        return SettingsBackup.Backup(
            vlessKeys = keys,
            activeVpn = VpnProfileKind.VLESS,
            multiHopEntry = MultiHopEntryRef.Vless("entry"),
        )
    }

    @Test fun `v5 backup round trips multi-hop entry`() {
        val restored = SettingsBackup.fromJson(
            SettingsBackup.toJson(backupWithVlessEntry()),
        )

        assertEquals(MultiHopEntryRef.Vless("entry"), restored?.multiHopEntry)
        assertEquals("exit", restored?.vlessKeys?.activeId)
    }

    @Test fun `v4 backup imports with multi-hop disabled`() {
        val legacy = JSONObject(SettingsBackup.toJson(backupWithVlessEntry())).apply {
            put("v", 4)
            remove("multiHopEntry")
        }

        assertNull(SettingsBackup.fromJson(legacy.toString())?.multiHopEntry)
    }

    @Test fun `v5 backup rejects entry equal to active exit`() {
        val invalid = JSONObject(SettingsBackup.toJson(backupWithVlessEntry().copy(multiHopEntry = null))).apply {
            put("multiHopEntry", "vless:exit")
        }

        assertNull(SettingsBackup.fromJson(invalid.toString()))
    }

    @Test fun `v5 backup rejects subscription as entry`() {
        val base = backupWithVlessEntry()
        val withSubscription = base.copy(
            vlessKeys = base.vlessKeys.copy(
                items = base.vlessKeys.items + VlessKey(
                    id = "subscription",
                    name = "Subscription",
                    uri = "https://subscription.example/token",
                ),
            ),
            multiHopEntry = null,
        )
        val invalid = JSONObject(SettingsBackup.toJson(withSubscription)).apply {
            put("multiHopEntry", "vless:subscription")
        }

        assertNull(SettingsBackup.fromJson(invalid.toString()))
    }

    @Test fun `v5 backup rejects unavailable WARP entry`() {
        val invalid = JSONObject(SettingsBackup.toJson(backupWithVlessEntry().copy(multiHopEntry = null))).apply {
            put("multiHopEntry", "warp")
        }

        assertNull(SettingsBackup.fromJson(invalid.toString()))
    }
}
