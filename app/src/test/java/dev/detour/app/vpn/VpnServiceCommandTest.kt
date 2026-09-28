package dev.detour.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnServiceCommandTest {
    @Test
    fun `app actions are honored regardless of always-on`() {
        assertEquals(VpnServiceCommand.START, classifyVpnServiceCommand(TriVpnService.ACTION_START, false, false))
        assertEquals(VpnServiceCommand.STOP, classifyVpnServiceCommand(TriVpnService.ACTION_STOP, true, true))
        assertEquals(VpnServiceCommand.RESTART, classifyVpnServiceCommand(TriVpnService.ACTION_RESTART, false, true))
    }

    @Test
    fun `platform always-on start brings the tunnel up even before isAlwaysOn reports it`() {
        assertEquals(VpnServiceCommand.START, classifyVpnServiceCommand("android.net.VpnService", false, false))
        assertEquals(VpnServiceCommand.START, classifyVpnServiceCommand("android.net.VpnService", true, false))
    }

    @Test
    fun `null or unknown starts need confirmed always-on`() {
        assertEquals(VpnServiceCommand.START, classifyVpnServiceCommand(null, true, false))
        assertEquals(VpnServiceCommand.IGNORE, classifyVpnServiceCommand(null, false, false))
        assertEquals(VpnServiceCommand.IGNORE, classifyVpnServiceCommand("other", false, false))
    }

    @Test
    fun `system start does not restart a live session`() {
        assertEquals(VpnServiceCommand.IGNORE, classifyVpnServiceCommand("android.net.VpnService", true, true))
    }
}
