package dev.triplet.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilitySmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test fun dnsCustomInputExposesAccessibleLabel() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.cd_settings))
            .performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.nav_dns))
            .performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.dns_custom))
            .performClick()

        rule.onNodeWithContentDescription(rule.activity.getString(R.string.dns_custom_label))
            .assertIsDisplayed()
    }
}
