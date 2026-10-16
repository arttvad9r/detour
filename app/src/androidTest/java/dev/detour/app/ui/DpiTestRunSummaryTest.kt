package dev.detour.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import dev.detour.app.R
import dev.detour.app.core.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DpiTestRunSummaryTest {
    @get:Rule val rule = createComposeRule()
    private var applies = 0
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val strategy = DpiProxyTestCatalog.strategies.first()
    private fun launch(successes: Int, font: Float = 1f, applying: Boolean = false, applied: Boolean = false) {
        val result = DpiProxyTestResultSummary(strategy, true, true, 1,
            if (successes == 3) 1 else 0, successes, 3, if (successes > 0) 10 else null,
            listOf(DpiProxyTestServiceSummary("youtube", 1, if (successes == 3) 1 else 0, successes, 3)))
        val run = DpiProxyTestRun("fixture", 1000, setOf("youtube"), DpiProxyTestConfig(), listOf(result))
        rule.setContent {
            val theme = AppTheme.DETOUR_DARK
            CompositionLocalProvider(LocalDetourTheme provides theme, LocalDetourColors provides theme.colors) {
                MaterialTheme(colorScheme = colorSchemeFor(theme.colors, theme.dark), typography = AppTypography) {
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(font)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            DpiTestRunSummary(run, strategy.id.takeIf { applying }, strategy.id.takeIf { applied },
                                null, true, onApply = { applies++ })
                        }
                    }
                }
            }
        }
    }

    @Test fun zeroSuccessHasNoRecommendationAction() {
        launch(0)
        rule.onNodeWithText(context.getString(R.string.dpi_result_none)).assertIsDisplayed()
        rule.onNodeWithTag("dpi-apply-best").assertDoesNotExist()
    }

    @Test fun partialCandidateIsExplicitAndCanBeAppliedAtLargeFont() {
        launch(1, font = 2f)
        rule.onNodeWithText(context.getString(R.string.dpi_result_partial)).assertIsDisplayed()
        rule.onNodeWithTag("dpi-apply-best").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, applies) }
    }

    @Test fun savingPreventsDuplicateApply() {
        launch(3, applying = true)
        rule.onNodeWithTag("dpi-apply-best").performScrollTo().assertIsNotEnabled()
    }

    @Test fun savedCandidateDoesNotOfferAnotherApply() {
        launch(3, applied = true)
        rule.onNodeWithTag("dpi-apply-best").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText(context.getString(R.string.dpi_proxy_test_applied)).assertIsDisplayed()
    }
}
