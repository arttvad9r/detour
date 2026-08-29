package dev.triplet.app.ui

import dev.triplet.app.vpn.VpnState
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusStyleTest {
    @Test fun `failed status keeps a neutral surface in every theme`() {
        AppTheme.entries.forEach { theme ->
            val style = statusStyleFor(theme.colors, VpnState.Failed("test"))
            assertEquals(theme.colors.surface, style.container)
            assertEquals(theme.colors.error, style.content)
            assertEquals(theme.colors.error.copy(alpha = .45f), style.border)
        }
    }
}
