package dev.triplet.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnServiceCommandTest {
    @Test fun `app marked start remains a user start`() {
        assertEquals(
            VpnServiceCommand.START_USER,
            classifyVpnServiceCommand(
                action = DETOUR_VPN_ACTION_START,
                startedByApp = true,
                alwaysOn = false,
            ),
        )
    }

    @Test fun `unmarked starts are system starts only while Always-on is active`() {
        assertEquals(
            VpnServiceCommand.START_SYSTEM,
            classifyVpnServiceCommand(
                action = ANDROID_VPN_SERVICE_ACTION,
                startedByApp = false,
                alwaysOn = true,
            ),
        )
        assertEquals(
            VpnServiceCommand.START_SYSTEM,
            classifyVpnServiceCommand(
                action = null,
                startedByApp = false,
                alwaysOn = true,
            ),
        )
        assertEquals(
            VpnServiceCommand.IGNORE,
            classifyVpnServiceCommand(
                action = null,
                startedByApp = false,
                alwaysOn = false,
            ),
        )
    }

    @Test fun `stop and restart keep explicit semantics`() {
        assertEquals(
            VpnServiceCommand.STOP,
            classifyVpnServiceCommand(
                action = DETOUR_VPN_ACTION_STOP,
                startedByApp = true,
                alwaysOn = true,
            ),
        )
        assertEquals(
            VpnServiceCommand.RESTART,
            classifyVpnServiceCommand(
                action = DETOUR_VPN_ACTION_RESTART,
                startedByApp = true,
                alwaysOn = true,
            ),
        )
    }

    @Test fun `unknown app-marked actions never start a tunnel`() {
        assertEquals(
            VpnServiceCommand.IGNORE,
            classifyVpnServiceCommand(
                action = "dev.triplet.app.action.UNKNOWN",
                startedByApp = true,
                alwaysOn = true,
            ),
        )
    }
}
