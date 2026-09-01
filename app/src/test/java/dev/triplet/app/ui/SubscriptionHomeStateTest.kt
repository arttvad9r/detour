package dev.triplet.app.ui

import dev.triplet.app.core.VpnProfileKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionHomeStateTest {
    @Test fun `subscription presentation uses provider host without treating it as warp`() {
        val presentation = homeProfilePresentation(
            activeVpn = VpnProfileKind.SUBSCRIPTION,
            vlessUri = "https://subscription.example/opaque-token",
            warpName = "ignored",
            warpEndpointCount = 99,
        )

        assertEquals("subscription.example", presentation.name)
        assertEquals("subscription.example", presentation.server)
        assertEquals(0, presentation.endpointCount)
    }
}
