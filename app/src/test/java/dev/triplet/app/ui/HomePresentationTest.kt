package dev.triplet.app.ui

import dev.triplet.app.core.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePresentationTest {
    @Test fun `protocol describes configured transports`() {
        assertEquals("Vless + DPI", homeProtocol("vless://key", mapOf("a" to AppRoute.DPI)))
        assertEquals("DPI", homeProtocol("", mapOf("a" to AppRoute.DPI)))
        assertEquals("Vless", homeProtocol("vless://key", emptyMap()))
        assertEquals("Vless", homeProtocol("", emptyMap()))
    }
}
