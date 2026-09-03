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
        selectedNode = "Node B",
    )
    private val keys = VlessKeys(listOf(subscription), subscription.id)

    @Test fun `v3 backup round trips subscription profile kind and selected node`() {
        val json = SettingsBackup.toJson(
            SettingsBackup.Backup(
                vlessKeys = keys,
                activeVpn = VpnProfileKind.SUBSCRIPTION,
            ),
        )

        val restored = SettingsBackup.fromJson(json)

        assertEquals(VpnProfileKind.SUBSCRIPTION, restored?.activeVpn)
        assertEquals(subscription.uri, restored?.vlessKeys?.active?.uri)
        assertEquals("Node B", restored?.vlessKeys?.active?.selectedNode)
        assertTrue(restored?.vlessKeys?.active?.uri?.startsWith("https://") == true)
    }

    @Test fun `backup keeps multiple subscriptions and inactive profiles`() {
        val vless = VlessKey(
            id = "vless",
            name = "VLESS",
            uri = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
                "?type=tcp&security=reality&fp=chrome&sni=example.com" +
                "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
                "&flow=xtls-rprx-vision",
        )
        val second = VlessKey(
            id = "subscription-2",
            name = "Second",
            uri = "https://second.example/token",
            selectedNode = "Node 7",
        )
        val all = VlessKeys(listOf(vless, subscription, second), second.id)

        val restored = SettingsBackup.fromJson(
            SettingsBackup.toJson(
                SettingsBackup.Backup(
                    vlessKeys = all,
                    activeVpn = VpnProfileKind.SUBSCRIPTION,
                ),
            ),
        )

        assertEquals(3, restored?.vlessKeys?.items?.size)
        assertEquals("Node B", restored?.vlessKeys?.items?.first { it.id == subscription.id }?.selectedNode)
        assertEquals("Node 7", restored?.vlessKeys?.items?.first { it.id == second.id }?.selectedNode)
        assertEquals(second.id, restored?.vlessKeys?.activeId)
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
