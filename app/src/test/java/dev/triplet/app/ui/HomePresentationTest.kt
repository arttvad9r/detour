package dev.triplet.app.ui

import dev.triplet.app.vpn.EffectiveRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePresentationTest {
    @Test fun `protocol describes effective transports`() {
        assertEquals(
            HomeProtocol.VLESS_DPI,
            homeProtocol(EffectiveRoutes(vpnPackages = setOf("vpn"), dpiPackages = setOf("dpi"))),
        )
        assertEquals(
            HomeProtocol.DPI,
            homeProtocol(EffectiveRoutes(vpnPackages = emptySet(), dpiPackages = setOf("dpi"))),
        )
        assertEquals(
            HomeProtocol.VLESS,
            homeProtocol(EffectiveRoutes(vpnPackages = setOf("vpn"), dpiPackages = emptySet())),
        )
        assertEquals(HomeProtocol.NONE, homeProtocol(EffectiveRoutes(emptySet(), emptySet())))
    }

    @Test fun `rejected shared uid routes do not advertise a transport`() {
        val rejected = EffectiveRoutes(emptySet(), emptySet(), sharedUidConflict = setOf(10001))
        assertEquals(HomeProtocol.NONE, homeProtocol(rejected))
    }
}
