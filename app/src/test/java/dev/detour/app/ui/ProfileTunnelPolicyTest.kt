package dev.detour.app.ui

import dev.detour.app.core.VpnProfileKind
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

    @Test fun `VLESS mutations do not affect selected WireGuard tunnel`() {
        assertEquals(
            ProfileTunnelAction.NONE,
            vlessMutationTunnelAction(VpnProfileKind.WARP, "active", "active", deleting = false),
        )
        assertEquals(
            ProfileTunnelAction.NONE,
            vlessMutationTunnelAction(VpnProfileKind.WARP, "active", "active", deleting = true),
        )
    }

    @Test fun `selected WireGuard edit restarts but delete stops`() {
        assertEquals(
            ProfileTunnelAction.RESTART,
            wireGuardMutationTunnelAction(
                VpnProfileKind.WARP,
                activeWireGuardId = "awg",
                profileId = "awg",
                deleting = false,
            ),
        )
        assertEquals(
            ProfileTunnelAction.STOP,
            wireGuardMutationTunnelAction(
                VpnProfileKind.WARP,
                activeWireGuardId = "awg",
                profileId = "awg",
                deleting = true,
            ),
        )
    }

    @Test fun `inactive WireGuard mutation leaves active tunnel alone`() {
        assertEquals(
            ProfileTunnelAction.NONE,
            wireGuardMutationTunnelAction(
                VpnProfileKind.WARP,
                activeWireGuardId = "awg",
                profileId = "warp",
                deleting = false,
            ),
        )
        assertEquals(
            ProfileTunnelAction.NONE,
            wireGuardMutationTunnelAction(
                VpnProfileKind.WARP,
                activeWireGuardId = "awg",
                profileId = "warp",
                deleting = true,
            ),
        )
        assertEquals(
            ProfileTunnelAction.NONE,
            wireGuardMutationTunnelAction(
                VpnProfileKind.VLESS,
                activeWireGuardId = "awg",
                profileId = "awg",
                deleting = false,
            ),
        )
    }
}
