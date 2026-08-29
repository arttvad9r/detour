package dev.triplet.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {
    @Test fun `popular palette set has one light and three dark themes`() {
        assertEquals(
            listOf("catppuccin_latte", "catppuccin_mocha", "gruvbox_dark", "dracula"),
            AppTheme.entries.map { it.id },
        )
        assertFalse(AppTheme.CATPPUCCIN_LATTE.dark)
        assertTrue(AppTheme.CATPPUCCIN_MOCHA.dark)
        assertTrue(AppTheme.GRUVBOX_DARK.dark)
        assertTrue(AppTheme.DRACULA.dark)
    }

    @Test fun `legacy Detour theme ids migrate to closest replacement`() {
        assertEquals(AppTheme.CATPPUCCIN_LATTE, AppTheme.byId("lavenda"))
        assertEquals(AppTheme.CATPPUCCIN_LATTE, AppTheme.byId("ocean"))
        assertEquals(AppTheme.CATPPUCCIN_MOCHA, AppTheme.byId("midnight"))
        assertEquals(AppTheme.GRUVBOX_DARK, AppTheme.byId("graphite"))
        assertEquals(AppTheme.CATPPUCCIN_LATTE, AppTheme.byId(""))
    }
}
