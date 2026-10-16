package dev.detour.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.detour.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PreReleaseUiRegressionTest {
    @get:Rule val rule = createComposeRule()

    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            val theme = AppTheme.DETOUR_DARK
            CompositionLocalProvider(LocalDetourTheme provides theme, LocalDetourColors provides theme.colors) {
                MaterialTheme(colorScheme = colorSchemeFor(theme.colors, theme.dark), typography = AppTypography) {
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                        Column(Modifier.width(320.dp)) { block() }
                    }
                }
            }
        }
    }

    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    @Test fun sliderValueStaysOnOneLineAtLargeFont() {
        val title = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dpi_proxy_test_concurrency)
        content { ProxySliderRow(title, "More checks use more resources.", 20, 1..32, true, {}) }
        val result = layout("20")
        assertEquals(1, result.lineCount)
        // The paragraph can retain the parent's width after Text wraps its own bounds.
        // Check rendered glyph bounds, not the unused paragraph container width.
        assertEquals(2, result.getLineEnd(0, visibleEnd = true))
        assertTrue(result.getLineLeft(0) >= 0f)
        assertTrue(result.getLineRight(0) <= result.size.width + 0.5f)
        assertTrue(result.getLineBottom(0) <= result.size.height + 0.5f)
    }

    @Test fun subscriptionHeadingHasItsOwnWidthAtLargeFont() {
        val title = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.subscription_runtime_title)
        content { SubscriptionRuntimeHeader(false, false, true, true, {}, {}) }
        val result = layout(title)
        assertTrue("Heading must not become a column of letters", result.lineCount <= 3)
        (0 until result.lineCount).forEach { line ->
            assertTrue(result.getLineRight(line) <= result.size.width + 0.5f)
            assertTrue(result.getLineBottom(line) <= result.size.height + 0.5f)
        }
    }
}
