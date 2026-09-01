package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionSettingsBackupTest {
    private val subscription = VlessKey(
        id = "subscription",
        name = "Subscription",
        uri = "https://subscription.example/opaque-token",
    )
    private val keys = VlessKeys(listOf(subscription), subscription.id)

    @Test fun `v3 backup round trips subscription profile kind`() {
        val json = SettingsBackup.toJson(
            SettingsBackup.Backup(
                vlessKeys = keys,
                activeVpn = VpnProfileKind.SUBSCRIPTION,
            ),
        )

        val restored = SettingsBackup.fromJson(json)

        assertEquals(VpnProfileKind.SUBSCRIPTION, restored?.activeVpn)
        assertEquals(subscription.uri, restored?.vlessKeys?.active?.uri)
        assertTrue(restored?.vlessKeys?.active?.uri?.startsWith("https://") == true)
    }

    @Test fun `v3 backup rejects subscription key mislabeled as vless`() {
        val json = SettingsBackup.toJson(
            SettingsBackup.Backup(
                vlessKeys = keys,
                activeVpn = VpnProfileKind.VLESS,
            ),
        )

        assertNull(SettingsBackup.fromJson(json))
    }
}
