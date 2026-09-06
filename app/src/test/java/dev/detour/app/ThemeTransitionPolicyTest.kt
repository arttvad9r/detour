package dev.detour.app

import dev.detour.app.ui.Motion
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTransitionPolicyTest {
    @Test fun `light dark transitions snap instead of interpolating foreground and background`() {
        assertEquals(0, themeTransitionDuration(previousDark = false, targetDark = true))
        assertEquals(0, themeTransitionDuration(previousDark = true, targetDark = false))
    }

    @Test fun `same brightness mode keeps normal theme motion`() {
        assertEquals(Motion.THEME_MS, themeTransitionDuration(previousDark = true, targetDark = true))
        assertEquals(Motion.THEME_MS, themeTransitionDuration(previousDark = false, targetDark = false))
    }
}
