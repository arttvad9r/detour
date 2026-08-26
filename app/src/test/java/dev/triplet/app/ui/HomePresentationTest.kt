package dev.triplet.app.ui

import dev.triplet.app.core.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePresentationTest {
    @Test fun `protocol describes configured transports`() {
        assertEquals(HomeProtocol.VLESS_DPI, homeProtocol(mapOf("a" to AppRoute.DPI, "b" to AppRoute.VPN)))
        assertEquals(HomeProtocol.DPI, homeProtocol(mapOf("a" to AppRoute.DPI)))
        assertEquals(HomeProtocol.VLESS, homeProtocol(mapOf("a" to AppRoute.VPN)))
        assertEquals(HomeProtocol.NONE, homeProtocol(emptyMap()))
    }
}
