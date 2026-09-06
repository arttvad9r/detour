package dev.detour.app.ui

import dev.detour.app.R
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.vpn.VpnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StatusStyleTest {
    @Test fun `active status uses semantic success palette in every theme`() {
        AppTheme.entries.forEach { theme ->
            val style = statusStyleFor(theme.colors, VpnState.Active)
            assertEquals(theme.colors.activeSoft, style.container)
            assertEquals(theme.colors.activeStrong, style.content)
            assertEquals(theme.colors.activeBorder, style.border)
            assertNotEquals(theme.colors.accent, style.content)
        }
    }

    @Test fun `starting status remains branded accent in every theme`() {
        AppTheme.entries.forEach { theme ->
            val style = statusStyleFor(theme.colors, VpnState.Starting)
            assertEquals(theme.colors.accentSoft, style.container)
            assertEquals(theme.colors.accent, style.content)
            assertEquals(theme.colors.accentBorder, style.border)
        }
    }

    @Test fun `failed status keeps a neutral surface in every theme`() {
        AppTheme.entries.forEach { theme ->
            val style = statusStyleFor(theme.colors, VpnState.Failed("test"))
            assertEquals(theme.colors.surface, style.container)
            assertEquals(theme.colors.error, style.content)
            assertEquals(theme.colors.error.copy(alpha = .45f), style.border)
        }
    }

    @Test fun `subscription home protocol uses vless labels`() {
        assertEquals(
            R.string.protocol_vless,
            homeProtocolLabelRes(HomeProtocol.VLESS, VpnProfileKind.SUBSCRIPTION),
        )
        assertEquals(
            R.string.protocol_vless_dpi,
            homeProtocolLabelRes(HomeProtocol.VLESS_DPI, VpnProfileKind.SUBSCRIPTION),
        )
    }
}
