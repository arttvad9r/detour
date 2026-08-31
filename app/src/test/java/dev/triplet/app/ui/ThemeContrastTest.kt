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
            val surface = theme.colors.surface
            val secondary = composite(theme.colors.textSecondary, surface)
            val muted = composite(theme.colors.textMuted, surface)
            assertTrue(
                "${theme.id} secondary contrast=${contrast(secondary, surface)}",
                contrast(secondary, surface) >= MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "${theme.id} muted contrast=${contrast(muted, surface)}",
                contrast(muted, surface) >= MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test fun `selected and error small text keep readable contrast`() {
        AppTheme.entries.forEach { theme ->
            val colors = theme.colors
            val selectedRow = composite(colors.accentSoft, colors.surface)
            val selectedSecondary = composite(colors.textSecondary, selectedRow)
            val selectedSegment = composite(colors.accentSoft, colors.surfaceSoft)

            assertTrue(
                "${theme.id} selected secondary contrast=${contrast(selectedSecondary, selectedRow)}",
                contrast(selectedSecondary, selectedRow) >= MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "${theme.id} selected segment contrast=${contrast(colors.textPrimary, selectedSegment)}",
                contrast(colors.textPrimary, selectedSegment) >= MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "${theme.id} error contrast=${contrast(colors.error, colors.background)}",
                contrast(colors.error, colors.background) >= MIN_TEXT_CONTRAST,
            )
        }
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
