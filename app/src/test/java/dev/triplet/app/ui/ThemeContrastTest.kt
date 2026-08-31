package dev.triplet.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemeContrastTest {
    @Test fun `secondary and muted text keep readable contrast on cards`() {
        AppTheme.entries.forEach { theme ->
            val c = theme.colors
            assertContrast(theme, "secondary/surface", c.textSecondary, c.surface)
            assertContrast(theme, "muted/surface", c.textMuted, c.surface)
        }
    }

    @Test fun `secondary text keeps readable contrast on selected rows`() {
        AppTheme.entries.forEach { theme ->
            val c = theme.colors
            val selectedRow = composite(c.accentSoft, c.surface)
            assertContrast(theme, "secondary/selected-row", c.textSecondary, selectedRow)
        }
    }

    @Test fun `selected segment text keeps readable contrast`() {
        AppTheme.entries.forEach { theme ->
            val c = theme.colors
            val selectedSegment = composite(c.accentSoft, c.surfaceSoft)
            assertContrast(theme, "primary/selected-segment", c.textPrimary, selectedSegment)
        }
    }

    @Test fun `error text keeps readable contrast on screen background`() {
        AppTheme.entries.forEach { theme ->
            val c = theme.colors
            assertContrast(theme, "error/background", c.error, c.background)
        }
    }

    private fun assertContrast(theme: AppTheme, pair: String, foreground: Color, background: Color) {
        val effectiveForeground = composite(foreground, background)
        val ratio = contrast(effectiveForeground, background)
        assertTrue(
            "${theme.id} $pair contrast=$ratio",
            ratio >= MIN_TEXT_CONTRAST,
        )
    }

    private fun composite(foreground: Color, background: Color): Color {
        if (foreground.alpha >= 0.999f) return foreground
        val a = foreground.alpha
        return Color(
            red = foreground.red * a + background.red * (1f - a),
            green = foreground.green * a + background.green * (1f - a),
            blue = foreground.blue * a + background.blue * (1f - a),
            alpha = 1f,
        )
    }

    private fun contrast(a: Color, b: Color): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private companion object {
        const val MIN_TEXT_CONTRAST = 4.5
    }
}
