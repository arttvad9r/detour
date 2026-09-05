package dev.triplet.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAutoConnectPolicyTest {
    @Test fun `validated internet network can trigger foreground auto-connect`() {
        assertTrue(
            shouldAttemptForegroundAutoConnect(
                hasInternet = true,
                validated = true,
                vpnTransport = false,
            ),
        )
    }

    @Test fun `unvalidated captive or local networks do not trigger`() {
        assertFalse(
            shouldAttemptForegroundAutoConnect(
                hasInternet = true,
                validated = false,
                vpnTransport = false,
            ),
        )
        assertFalse(
            shouldAttemptForegroundAutoConnect(
                hasInternet = false,
                validated = true,
                vpnTransport = false,
            ),
        )
    }

    @Test fun `vpn default network never recursively starts vpn`() {
        assertFalse(
            shouldAttemptForegroundAutoConnect(
                hasInternet = true,
                validated = true,
                vpnTransport = true,
            ),
        )
    }
}
