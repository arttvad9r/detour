package dev.detour.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SubscriptionCatalogPresentationTest {
    @Test
    fun `catalog parser reads nodes and provider metadata`() {
        val catalog = parseSubscriptionCatalog(
            """
            {
              "nodes": [
                {"name":"Finland 1","type":"vless"},
                {"name":"Finland 1","type":"vless"},
                {"name":"Legacy","type":"vmess"}
              ],
              "meta": {
                "title":"Detour Premium",
                "uploadBytes":100,
                "downloadBytes":400,
                "totalBytes":1000,
                "expireAtUnix":1893456000
              }
            }
            """.trimIndent(),
        )

        assertEquals(listOf("Finland 1"), catalog.nodes.map { it.name })
        val info = catalog.info!!
        assertEquals("Detour Premium", info.title)
        assertEquals(500L, info.usedBytes)
        assertEquals(500L, info.remainingBytes)
        assertEquals(0.5f, info.usedFraction)
        assertTrue(info.hasExpiry)
    }

    @Test
    fun `catalog without metadata has no info`() {
        val catalog = parseSubscriptionCatalog("""{"nodes":[{"name":"A","type":"vless"}]}""")
        assertEquals(1, catalog.nodes.size)
        assertNull(catalog.info)
    }

    @Test
    fun `malformed catalog is empty`() {
        val catalog = parseSubscriptionCatalog("not json")
        assertTrue(catalog.nodes.isEmpty())
        assertNull(catalog.info)
    }

    @Test
    fun `usage beyond the plan is clamped`() {
        val info = SubscriptionInfo(usedBytes = 1500L, totalBytes = 1000L)
        assertEquals(0L, info.remainingBytes)
        assertEquals(1f, info.usedFraction)
    }

    @Test
    fun `arrange filters by name and sorts by latency with untested last`() {
        val nodes = listOf("Germany 1", "Finland 2", "Germany 2", "Sweden 1")
            .map { SubscriptionCatalogNode(it, "vless") }
        val latency = mapOf("Germany 2" to 40, "Finland 2" to 90, "Germany 1" to 60)

        assertEquals(
            listOf("Germany 1", "Germany 2"),
            arrangeSubscriptionNodes(nodes, " germany ", SubscriptionSortOrder.DEFAULT, latency).map { it.name },
        )
        assertEquals(
            listOf("Germany 2", "Germany 1", "Finland 2", "Sweden 1"),
            arrangeSubscriptionNodes(nodes, "", SubscriptionSortOrder.LATENCY, latency).map { it.name },
        )
    }

    @Test
    fun `days left rounds up and turns negative after expiry`() {
        val now = TimeUnit.DAYS.toMillis(100)
        val inTwoAndHalfDays = TimeUnit.MILLISECONDS.toSeconds(now + TimeUnit.HOURS.toMillis(60))
        assertEquals(3L, subscriptionDaysLeft(inTwoAndHalfDays, now))
        assertEquals(-1L, subscriptionDaysLeft(TimeUnit.MILLISECONDS.toSeconds(now) - 1, now))
    }

    @Test
    fun `provider title replaces only the auto-generated host name`() {
        assertTrue(shouldAdoptSubscriptionTitle("sub.example.com", "sub.example.com", "My VPN"))
        assertTrue(shouldAdoptSubscriptionTitle("", "sub.example.com", "My VPN"))
        assertFalse(shouldAdoptSubscriptionTitle("Work", "sub.example.com", "My VPN"))
        assertFalse(shouldAdoptSubscriptionTitle("My VPN", "sub.example.com", "My VPN"))
    }
}
