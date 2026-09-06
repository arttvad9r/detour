package dev.triplet.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.triplet.app.core.DpiBackend
import dev.triplet.app.core.DpiProxyTestCatalog
import dev.triplet.app.core.ProbeAuth
import java.util.concurrent.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DpiProxyTestSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test fun proxyTestScreenExposesStrategiesAndCustomWorkspaceBeforeRun() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.cd_settings))
            .performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.nav_dpi))
            .performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.dpi_proxy_test_open))
            .assertIsDisplayed()
            .performClick()

        rule.onNodeWithText(rule.activity.getString(R.string.dpi_proxy_test_title))
            .assertIsDisplayed()
        rule.onNodeWithText(
            rule.activity.getString(R.string.dpi_proxy_test_strategy_number, 1),
        ).assertIsDisplayed()

        rule.onNodeWithText(rule.activity.getString(R.string.dpi_proxy_test_hide_strategies))
            .performClick()

        val scroll = rule.onNode(hasScrollAction())
        val customTitle = rule.activity.getString(R.string.dpi_proxy_test_custom_title)
        scroll.performScrollToNode(hasText(customTitle))
        rule.onNodeWithText(customTitle).assertIsDisplayed()

        scroll.performScrollToNode(hasText("YouTube"))
        rule.onNodeWithText("YouTube").assertIsDisplayed()
        scroll.performScrollToNode(hasText("GoogleVideo"))
        rule.onNodeWithText("GoogleVideo").assertIsDisplayed()

        val startLabel = rule.activity.getString(R.string.dpi_proxy_test_start)
        scroll.performScrollToNode(hasText(startLabel))
        rule.onNodeWithText(startLabel).assertIsDisplayed()

        val historyTitle = rule.activity.getString(R.string.dpi_proxy_test_history_title)
        scroll.performScrollToNode(hasText(historyTitle))
        rule.onNodeWithText(historyTitle).assertIsDisplayed()
    }

    @Test fun cancelledBackendStartDoesNotLeaveProcessRunning() {
        val backend = DpiBackend(rule.activity)
        val strategy = DpiProxyTestCatalog.strategies.first()
        val port = 10828
        try {
            assertTrue(
                backend.start(
                    strategyArgs = strategy.args,
                    port = port,
                    credentials = ProbeAuth.current(),
                ),
            )
            backend.stop()
            assertFalse(backend.isAlive())

            var cancellationObserved = false
            try {
                backend.start(
                    strategyArgs = strategy.args,
                    port = port,
                    credentials = ProbeAuth.current(),
                    cancelled = { true },
                )
            } catch (_: CancellationException) {
                cancellationObserved = true
            }
            assertTrue(cancellationObserved)
            assertFalse(backend.isAlive())
        } finally {
            backend.stop()
        }
    }
}
