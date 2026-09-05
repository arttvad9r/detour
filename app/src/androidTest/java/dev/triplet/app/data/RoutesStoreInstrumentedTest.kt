package dev.triplet.app.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.triplet.app.TripletApp
import dev.triplet.app.core.AmneziaWgOptions
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.SettingsBackup
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.core.WarpProfile
import dev.triplet.app.core.WarpProxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device DataStore behavior: atomic key mutations, duplicate-id rejection,
 * import policy, and Android-Keystore-backed encryption of VPN credentials.
 */
@RunWith(AndroidJUnit4::class)
class RoutesStoreInstrumentedTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    // DataStore is file-locked: always reuse the app-wide singleton (see TriVpnService).
    private val store = (ctx as TripletApp).routesStore

    private val validUri =
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
        "?type=tcp&security=reality&fp=chrome&sni=example.com" +
        "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
        "&flow=xtls-rprx-vision"

    @Test fun addUpdateDeleteRoundTrip() = runBlocking {
        store.addVlessKey(VlessKey("t1", "One", validUri))
        store.updateVlessKey(VlessKey("t1", "Renamed", validUri))
        var s = store.snapshot()
        assertEquals("Renamed", s.vlessKeys.items.single { it.id == "t1" }.name)

        store.deleteVlessKey("t1")
        s = store.snapshot()
        assertTrue(s.vlessKeys.items.none { it.id == "t1" })
    }

    @Test fun duplicateIdRejected() = runBlocking {
        try {
            runCatching { store.deleteVlessKey("dup") }
            store.addVlessKey(VlessKey("dup", "A", validUri))
            store.addVlessKey(VlessKey("dup", "B", validUri))
            throw AssertionError("duplicate id must be rejected")
        } catch (expected: IllegalArgumentException) {
            // storage stays consistent with the single original entry
            assertEquals(1, store.snapshot().vlessKeys.items.count { it.id == "dup" })
        } finally {
            store.deleteVlessKey("dup")
        }
    }

    @Test fun importedAutoConnectIsForcedOff() = runBlocking {
        val backup = SettingsBackup.Backup(
            vlessKeys = VlessKeys(listOf(VlessKey("k", "K", validUri)), "k"),
            presetId = DpiPreset.RECOMMENDED.id,
            autoConnect = true,
            routes = mapOf("com.example.imported" to AppRoute.VPN.name),
        )
        store.restoreBackup(backup)
        val s = store.snapshot()
        assertFalse("import must not enable auto-connect", s.autoConnect)
        assertEquals(AppRoute.VPN, s.routes["com.example.imported"])
    }

    @Test fun subscriptionSelectionsSurviveBackupParserAndDataStoreRestore() = runBlocking {
        val before = store.snapshot()
        val original = SettingsBackup.Backup(
            vlessKeys = before.vlessKeys,
            warpProfile = before.warpProfile,
            activeVpn = before.activeVpn,
            presetId = before.preset.id,
            dpiCustomArgs = before.dpiCustomArgs,
            autoConnect = before.autoConnect,
            themeId = before.themeId,
            dnsId = before.dnsId,
            dnsCustom = before.dnsCustom,
            routes = before.routes.mapValues { it.value.name },
            showSystemApps = before.showSystemApps,
        )
        val expectedKeys = VlessKeys(
            items = listOf(
                VlessKey("direct", "Direct VLESS", validUri),
                VlessKey(
                    id = "sub-a",
                    name = "Subscription A",
                    uri = "https://a.subscription.example/opaque-a",
                    selectedNode = "DE Frankfurt",
                ),
                VlessKey(
                    id = "sub-b",
                    name = "Subscription B",
                    uri = "https://b.subscription.example/opaque-b",
                    selectedNode = "NL Amsterdam",
                ),
            ),
            activeId = "sub-a",
        )
        val exported = SettingsBackup.toJson(
            SettingsBackup.Backup(
                vlessKeys = expectedKeys,
                activeVpn = VpnProfileKind.SUBSCRIPTION,
                presetId = DpiPreset.RECOMMENDED.id,
                themeId = "catppuccin_latte",
                dnsId = "google",
            ),
        )
        val parsed = requireNotNull(SettingsBackup.fromJson(exported))

        try {
            store.restoreBackup(parsed)
            val restored = store.snapshot()
            assertEquals(expectedKeys, restored.vlessKeys)
            assertEquals(VpnProfileKind.SUBSCRIPTION, restored.activeVpn)
            assertEquals("DE Frankfurt", restored.vlessKeys.items.single { it.id == "sub-a" }.selectedNode)
            assertEquals("NL Amsterdam", restored.vlessKeys.items.single { it.id == "sub-b" }.selectedNode)
        } finally {
            store.restoreBackup(original)
            store.setAutoConnect(before.autoConnect)
            store.setSessionStartedAt(before.sessionStartedAt)
        }
    }

    @Test fun vpnCredentialsAreEncryptedAtRest() = runBlocking {
        val id = "encrypted-at-rest"
        val secretUri = validUri.replace("example.com:443", "at-rest-marker.detour.invalid:443")
        val privateMarker = "warp-private-at-rest-marker-2af74b8d"
        val warp = WarpProfile(
            id = "warp-encrypted-at-rest",
            name = "Encrypted WARP",
            proxies = listOf(
                WarpProxy(
                    name = "endpoint",
                    server = "warp.invalid",
                    port = 4500,
                    ip = "172.16.0.2",
                    privateKey = privateMarker,
                    publicKey = "warp-public-key",
                    reserved = listOf(1, 2, 3),
                    allowedIps = listOf("0.0.0.0/0"),
                    amnezia = AmneziaWgOptions(jc = 4),
                ),
            ),
        )

        try {
            runCatching { store.deleteVlessKey(id) }
            store.addVlessKey(VlessKey(id, "Encrypted", secretUri))
            store.setWarpProfile(warp)

            val snapshot = store.snapshot()
            assertEquals(secretUri, snapshot.vlessKeys.items.single { it.id == id }.uri)
            assertEquals(privateMarker, snapshot.warpProfile?.proxies?.single()?.privateKey)

            val raw = String(
                ctx.preferencesDataStoreFile("triplet_settings").readBytes(),
                Charsets.ISO_8859_1,
            )
            assertFalse("VLESS URI must not be stored as plaintext", raw.contains(secretUri))
            assertFalse("WARP private key must not be stored as plaintext", raw.contains(privateMarker))
        } finally {
            store.deleteVlessKey(id)
            store.deleteWarpProfile()
        }
    }
}
