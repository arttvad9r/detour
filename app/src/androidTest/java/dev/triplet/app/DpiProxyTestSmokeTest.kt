package dev.triplet.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DpiProxyTestSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test fun proxyTestScreenIsReachableWithReferenceDefaults() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.cd_settings))
            .performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.nav_dpi))
            .performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.dpi_proxy_test_open))
            .assertIsDisplayed()
            .performClick()

        rule.onNodeWithText(rule.activity.getString(R.string.dpi_proxy_test_title))
            .assertIsDisplayed()
        rule.onNodeWithText("YouTube").assertIsDisplayed()
        rule.onNodeWithText("GoogleVideo").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.dpi_proxy_test_start))
            .assertIsDisplayed()
    }
}
