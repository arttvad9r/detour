package dev.detour.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundAutoConnectPolicyTest {
    @Test fun `validated wifi network maps to wifi trigger`() {
        assertEquals(
            AutoConnectTrigger.WIFI,
            foregroundAutoConnectTrigger(
                hasInternet = true,
                validated = true,
                vpnTransport = false,
                wifiTransport = true,
                cellularTransport = false,
            ),
        )
    }

    @Test fun `validated cellular network maps to cellular trigger`() {
        assertEquals(
            AutoConnectTrigger.CELLULAR,
            foregroundAutoConnectTrigger(
                hasInternet = true,
                validated = true,
                vpnTransport = false,
                wifiTransport = false,
                cellularTransport = true,
            ),
        )
    }

    @Test fun `unvalidated captive or local networks do not trigger`() {
        assertNull(
            foregroundAutoConnectTrigger(
                hasInternet = true,
                validated = false,
                vpnTransport = false,
                wifiTransport = true,
                cellularTransport = false,
            ),
        )
        assertNull(
            foregroundAutoConnectTrigger(
                hasInternet = false,
                validated = true,
                vpnTransport = false,
                wifiTransport = false,
                cellularTransport = true,
            ),
        )
    }

    @Test fun `vpn and unsupported transports never recursively trigger`() {
        assertNull(
            foregroundAutoConnectTrigger(
                hasInternet = true,
                validated = true,
                vpnTransport = true,
                wifiTransport = true,
                cellularTransport = false,
            ),
        )
        assertNull(
            foregroundAutoConnectTrigger(
                hasInternet = true,
                validated = true,
                vpnTransport = false,
                wifiTransport = false,
                cellularTransport = false,
            ),
        )
    }
}
