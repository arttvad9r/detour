package dev.triplet.app.ui

import dev.triplet.app.core.VpnProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionHomeStateTest {
    @Test fun `subscription presentation shows selected server instead of provider host`() {
        val presentation = homeProfilePresentation(
            activeVpn = VpnProfileKind.SUBSCRIPTION,
            vlessUri = "https://subscription.example/opaque-token",
            warpName = "ignored",
            warpEndpointCount = 99,
            subscriptionNode = "Germany 01",
        )

        assertEquals("subscription.example", presentation.name)
        assertEquals("Germany 01", presentation.server)
        assertEquals(0, presentation.endpointCount)
    }

    @Test fun `subscription presentation does not present provider host as selected server`() {
        val presentation = homeProfilePresentation(
            activeVpn = VpnProfileKind.SUBSCRIPTION,
            vlessUri = "https://subscription.example/opaque-token",
            warpName = null,
            warpEndpointCount = 0,
        )

        assertEquals("subscription.example", presentation.name)
        assertNull(presentation.server)
    }
}
