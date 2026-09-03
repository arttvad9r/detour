package dev.triplet.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class LatencyStyleTest {
    @Test fun `latency tone boundaries match ui policy`() {
        assertEquals(LatencyTone.UNAVAILABLE, latencyToneForMs(null))
        assertEquals(LatencyTone.GOOD, latencyToneForMs(0))
        assertEquals(LatencyTone.GOOD, latencyToneForMs(99))
        assertEquals(LatencyTone.NEUTRAL, latencyToneForMs(100))
        assertEquals(LatencyTone.NEUTRAL, latencyToneForMs(200))
        assertEquals(LatencyTone.BAD, latencyToneForMs(201))
    }

    @Test fun `latency colors remain readable on cards`() {
        AppTheme.entries.forEach { theme ->
            listOf(99, 100, 200, 201).forEach { delayMs ->
                val background = theme.colors.surface
                val color = composite(latencyColorFor(theme, delayMs), background)
                val ratio = contrast(color, background)
                assertTrue(
                    "${theme.id} delay=$delayMs contrast=$ratio",
                    ratio >= MIN_TEXT_CONTRAST,
                )
            }
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
