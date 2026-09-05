package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessKeysTest {
    private val validUri =
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
        "?type=tcp&security=reality&fp=chrome&sni=example.com" +
        "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
        "&flow=xtls-rprx-vision"
    private val subscriptionUri = "https://subscription.example/token"

    @Test
    fun `legacy uri migrates to one active key`() {
        val keys = VlessKeys.fromStored("", "vless://legacy")

        assertEquals(1, keys.items.size)
        assertEquals("vless://legacy", keys.items.single().uri)
        assertEquals(keys.items.single().id, keys.activeId)
    }

    @Test
    fun `legacy migration is stable across reads`() {
        val first = VlessKeys.fromStored("", "vless://legacy")
        val second = VlessKeys.fromStored("", "vless://legacy")

        assertEquals(first, second)
    }

    @Test
    fun `json round trip preserves subscription state`() {
        val original = VlessKeys(
            listOf(
                VlessKey("a", "Primary", validUri),
                VlessKey(
                    "b",
                    "Subscription",
                    subscriptionUri,
                    selectedNode = "Node B",
                    favoriteNodes = setOf("Node A", "Node B"),
                    subscriptionSelectionMode = SubscriptionSelectionMode.AUTO,
                    subscriptionUpdateIntervalHours = 12,
                    subscriptionUpdatedAt = 1_780_000_000_000L,
                ),
            ),
            "b",
        )

        assertEquals(original, VlessKeys.fromJson(original.toJson()))
    }

    @Test
    fun `old json defaults new subscription behavior to manual and scheduled refresh disabled`() {
        val keys = VlessKeys.fromJson(
            """{"activeId":"b","items":[{"id":"b","name":"Subscription","uri":"$subscriptionUri"}]}""",
        )

        assertEquals(SubscriptionSelectionMode.MANUAL, keys.active?.subscriptionSelectionMode)
        assertEquals(null, keys.active?.subscriptionUpdateIntervalHours)
        assertEquals(null, keys.active?.subscriptionUpdatedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `json rejects invalid subscription selection mode`() {
        VlessKeys.fromJson(
            """{"activeId":"b","items":[{"id":"b","name":"Subscription","uri":"$subscriptionUri","subscriptionSelectionMode":"FASTESTISH"}]}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `json rejects invalid subscription update interval`() {
        VlessKeys.fromJson(
            """{"activeId":"b","items":[{"id":"b","name":"Subscription","uri":"$subscriptionUri","subscriptionUpdateIntervalHours":0}]}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `json rejects invalid subscription update timestamp`() {
        VlessKeys.fromJson(
            """{"activeId":"b","items":[{"id":"b","name":"Subscription","uri":"$subscriptionUri","subscriptionUpdatedAt":-1}]}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `json rejects malformed selected node`() {
        VlessKeys.fromJson(
            """{"activeId":"b","items":[{"id":"b","name":"Subscription","uri":"$subscriptionUri","selectedNode":"bad\nnode"}]}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `json rejects malformed favorite node`() {
        VlessKeys.fromJson(
            """{"activeId":"b","items":[{"id":"b","name":"Subscription","uri":"$subscriptionUri","favoriteNodes":["bad\nnode"]}]}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `json rejects malformed entries instead of dropping them`() {
        VlessKeys.fromJson("""{"items":[{"id":"a","name":"A","uri":"$validUri"},{}]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `json rejects unknown active id`() {
        VlessKeys.fromJson(
            """{"activeId":"missing","items":[{"id":"a","name":"A","uri":"$validUri"}]}""",
        )
    }

    @Test
    fun `empty storage has no keys`() {
        assertTrue(VlessKeys.fromStored("", "").items.isEmpty())
    }

    @Test fun `delete inactive preserves active`() {
        val keys = VlessKeys(listOf(VlessKey("a", "a", validUri), VlessKey("b", "b", validUri)), "b").delete("a")
        assertEquals("b", keys.activeId)
    }

    @Test fun `delete active requires explicit reselection`() {
        val remaining = VlessKeys(
            listOf(VlessKey("a", "a", validUri), VlessKey("b", "b", validUri)),
            "a",
        ).delete("a")
        assertEquals(null, remaining.activeId)
        assertEquals(listOf("b"), remaining.items.map { it.id })

        assertEquals(
            null,
            VlessKeys(listOf(VlessKey("a", "a", validUri)), "a").delete("a").activeId,
        )
    }

    @Test fun `explicit null active id survives json roundtrip`() {
        val keys = VlessKeys(
            listOf(VlessKey("a", "a", validUri), VlessKey("b", "b", validUri)),
            null,
        )
        assertEquals(keys, VlessKeys.fromJson(keys.toJson()))
    }

    @Test fun `corrupt key json does not resurrect legacy uri`() {
        val keys = VlessKeys.fromStored("{broken", validUri)
        assertTrue(keys.items.isEmpty())
        assertEquals(null, keys.activeId)
    }

    @Test fun `delete nonexistent is unchanged`() {
        val keys = VlessKeys(listOf(VlessKey("a", "a", validUri)), "a")
        assertEquals(keys, keys.delete("missing"))
    }
}
