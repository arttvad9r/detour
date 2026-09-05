package dev.triplet.app.ui

import dev.triplet.app.core.MultiHopEntryRef
import dev.triplet.app.core.VpnProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiHopProfilePolicyTest {
    @Test fun `editing or deleting VLESS entry affects tunnel even when it is not exit`() {
        val entry = MultiHopEntryRef.Vless("entry")

        assertEquals(
            ProfileTunnelAction.RESTART,
            vlessMutationTunnelAction(
                activeVpn = VpnProfileKind.SUBSCRIPTION,
                activeVlessId = "exit-subscription",
                keyId = "entry",
                deleting = false,
                multiHopEntry = entry,
            ),
        )
        assertEquals(
            ProfileTunnelAction.STOP,
            vlessMutationTunnelAction(
                activeVpn = VpnProfileKind.SUBSCRIPTION,
                activeVlessId = "exit-subscription",
                keyId = "entry",
                deleting = true,
                multiHopEntry = entry,
            ),
        )
    }

    @Test fun `replacing or deleting WARP entry affects non-WARP exit tunnel`() {
        assertEquals(
            ProfileTunnelAction.RESTART,
            warpMutationTunnelAction(
                activeVpn = VpnProfileKind.VLESS,
                deleting = false,
                multiHopEntry = MultiHopEntryRef.Warp,
            ),
        )
        assertEquals(
            ProfileTunnelAction.STOP,
            warpMutationTunnelAction(
                activeVpn = VpnProfileKind.VLESS,
                deleting = true,
                multiHopEntry = MultiHopEntryRef.Warp,
            ),
        )
    }

    @Test fun `unrelated profile mutation leaves tunnel alone`() {
        assertEquals(
            ProfileTunnelAction.NONE,
            vlessMutationTunnelAction(
                activeVpn = VpnProfileKind.SUBSCRIPTION,
                activeVlessId = "exit",
                keyId = "other",
                deleting = false,
                multiHopEntry = MultiHopEntryRef.Vless("entry"),
            ),
        )
    }

    @Test fun `entry matching newly selected exit is detected`() {
        assertTrue(MultiHopEntryRef.Vless("same").conflictsWithSelection(ProfileSelection.Vless("same")))
        assertFalse(MultiHopEntryRef.Vless("entry").conflictsWithSelection(ProfileSelection.Vless("exit")))
        assertTrue(MultiHopEntryRef.Warp.conflictsWithSelection(ProfileSelection.Warp))
        assertFalse(MultiHopEntryRef.Warp.conflictsWithSelection(ProfileSelection.Vless("exit")))
        assertFalse((null as MultiHopEntryRef?).conflictsWithSelection(ProfileSelection.Warp))
    }
}
