package dev.triplet.app.ui

import dev.triplet.app.core.VpnProfileKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileTunnelPolicyTest {
    @Test fun `inactive VLESS mutations do not touch tunnel`() {
        assertEquals(
            ProfileTunnelAction.NONE,
            vlessMutationTunnelAction(VpnProfileKind.VLESS, "active", "inactive", deleting = false),
        )
        assertEquals(
            ProfileTunnelAction.NONE,
            vlessMutationTunnelAction(VpnProfileKind.VLESS, "active", "new", deleting = true),
        )
    }

    @Test fun `selected VLESS edit restarts but delete stops`() {
        assertEquals(
            ProfileTunnelAction.RESTART,
            vlessMutationTunnelAction(VpnProfileKind.VLESS, "active", "active", deleting = false),
        )
        assertEquals(
            ProfileTunnelAction.STOP,
            vlessMutationTunnelAction(VpnProfileKind.VLESS, "active", "active", deleting = true),
        )
    }

    @Test fun `VLESS mutations do not affect selected WARP tunnel`() {
        assertEquals(
            ProfileTunnelAction.NONE,
            vlessMutationTunnelAction(VpnProfileKind.WARP, "active", "active", deleting = false),
        )
        assertEquals(
            ProfileTunnelAction.NONE,
            vlessMutationTunnelAction(VpnProfileKind.WARP, "active", "active", deleting = true),
        )
    }

    @Test fun `selected WARP replacement restarts but delete stops`() {
        assertEquals(
            ProfileTunnelAction.RESTART,
            warpMutationTunnelAction(VpnProfileKind.WARP, deleting = false),
        )
        assertEquals(
            ProfileTunnelAction.STOP,
            warpMutationTunnelAction(VpnProfileKind.WARP, deleting = true),
        )
        assertEquals(
            ProfileTunnelAction.NONE,
            warpMutationTunnelAction(VpnProfileKind.VLESS, deleting = false),
        )
    }
}
