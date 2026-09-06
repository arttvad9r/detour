package dev.detour.app.data

import dev.detour.app.core.MultiHopEntryRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MultiHopRoutesMappingTest {
    @Test fun `missing multi-hop entry stays disabled`() {
        assertNull(RoutesMapping.toSettings(emptyMap()).multiHopEntry)
    }

    @Test fun `VLESS entry reference is restored`() {
        assertEquals(
            MultiHopEntryRef.Vless("entry-id"),
            RoutesMapping.toSettings(mapOf("multi_hop_entry" to "vless:entry-id")).multiHopEntry,
        )
    }

    @Test fun `WARP entry reference is restored`() {
        assertSame(
            MultiHopEntryRef.Warp,
            RoutesMapping.toSettings(mapOf("multi_hop_entry" to "warp")).multiHopEntry,
        )
    }

    @Test fun `corrupt multi-hop entry stays fail closed`() {
        assertSame(
            MultiHopEntryRef.Invalid,
            RoutesMapping.toSettings(mapOf("multi_hop_entry" to "future:entry")).multiHopEntry,
        )
    }
}
