package dev.triplet.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnServiceCommandTest {
    @Test fun `user start remains distinct from Android always-on start`() {
        assertEquals(
            VpnServiceCommand.START_USER,
            classifyVpnServiceCommand(DETOUR_VPN_ACTION_START),
        )
        assertEquals(
            VpnServiceCommand.START_SYSTEM,
            classifyVpnServiceCommand(ANDROID_VPN_SERVICE_ACTION),
        )
    }

    @Test fun `stop and restart keep explicit semantics`() {
        assertEquals(VpnServiceCommand.STOP, classifyVpnServiceCommand(DETOUR_VPN_ACTION_STOP))
        assertEquals(VpnServiceCommand.RESTART, classifyVpnServiceCommand(DETOUR_VPN_ACTION_RESTART))
    }

    @Test fun `null and unknown actions do not start a tunnel`() {
        assertEquals(VpnServiceCommand.IGNORE, classifyVpnServiceCommand(null))
        assertEquals(VpnServiceCommand.IGNORE, classifyVpnServiceCommand("dev.triplet.app.action.UNKNOWN"))
    }
}
