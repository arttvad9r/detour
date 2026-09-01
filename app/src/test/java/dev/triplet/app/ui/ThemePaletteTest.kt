package dev.triplet.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {
    @Test fun `official Detour themes lead the community palette set`() {
        assertEquals(
            listOf(
                "detour_light",
                "detour_dark",
                "catppuccin_latte",
                "catppuccin_mocha",
                "gruvbox_dark",
                "dracula",
            ),
            AppTheme.entries.map { it.id },
        )
        assertFalse(AppTheme.DETOUR_LIGHT.dark)
        assertTrue(AppTheme.DETOUR_DARK.dark)
        assertFalse(AppTheme.CATPPUCCIN_LATTE.dark)
        assertTrue(AppTheme.CATPPUCCIN_MOCHA.dark)
        assertTrue(AppTheme.GRUVBOX_DARK.dark)
        assertTrue(AppTheme.DRACULA.dark)
    }

    @Test fun `legacy Detour theme ids migrate to branded replacements`() {
        assertEquals(AppTheme.DETOUR_LIGHT, AppTheme.byId("lavenda"))
        assertEquals(AppTheme.DETOUR_LIGHT, AppTheme.byId("ocean"))
        assertEquals(AppTheme.DETOUR_DARK, AppTheme.byId("midnight"))
        assertEquals(AppTheme.GRUVBOX_DARK, AppTheme.byId("graphite"))
        assertEquals(AppTheme.DETOUR_LIGHT, AppTheme.byId(""))
    }
}
