package dev.triplet.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import dev.triplet.app.TripletApp
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.SettingsBackup
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device DataStore behavior: atomic key mutations, duplicate-id rejection,
 * and import that never enables auto-connect.
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
}
