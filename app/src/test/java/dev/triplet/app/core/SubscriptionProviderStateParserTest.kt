package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionProviderStateParserTest {
    @Test fun `parses provider nodes health and subscription metadata`() {
        val state = SubscriptionProviderStateParser.parse(
            """
            {
              "name":"DETOUR_SUBSCRIPTION",
              "type":"Proxy",
              "vehicleType":"HTTP",
              "updatedAt":"2026-09-01T20:00:00Z",
              "subscriptionInfo":{"Upload":100,"Download":200,"Total":1000,"Expire":1900000000},
              "proxies":[
                {"name":"DE-1","type":"Vless","alive":true,"history":[{"delay":87}]},
                {"name":"NL-1","type":"Vless","alive":false,"history":[{"delay":0}]}
              ]
            }
            """.trimIndent(),
        )

        assertTrue(state.available)
        assertEquals(2, state.totalNodes)
        assertEquals(1, state.aliveNodes)
        assertEquals("DE-1", state.nodes[0].name)
        assertEquals(87, state.nodes[0].delayMs)
        assertFalse(state.nodes[1].alive)
        assertNull(state.nodes[1].delayMs)
        assertEquals(1000L, state.usage?.totalBytes)
        assertEquals("2026-09-01T20:00:00Z", state.updatedAt)
    }

    @Test fun `blank or unrelated provider is unavailable`() {
        assertFalse(SubscriptionProviderStateParser.parse("").available)
        assertFalse(
            SubscriptionProviderStateParser.parse(
                "{\"name\":\"other\",\"proxies\":[]}",
            ).available,
        )
    }
}
