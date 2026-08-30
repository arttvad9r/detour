package dev.triplet.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilitySmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test fun dnsCustomInputExposesAccessibleLabelAndValidationError() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.cd_settings))
            .performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.nav_dns))
            .performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.dns_custom))
            .performClick()

        val label = rule.activity.getString(R.string.dns_custom_label)
        val validationError = rule.activity.getString(R.string.dns_invalid_https)
        rule.onNodeWithContentDescription(label)
            .assertIsDisplayed()
            .performTextInput("not-a-resolver")

        rule.onNodeWithContentDescription(label)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, validationError))
    }

    @Test fun dnsChoicesPreserveSingleSelectionSemantics() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.cd_settings))
            .performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.nav_dns))
            .performClick()

        rule.onNodeWithText(rule.activity.getString(R.string.dns_google))
            .assertIsSelected()

        rule.onNodeWithText(rule.activity.getString(R.string.dns_custom))
            .performClick()
            .assertIsSelected()
    }

    @Test fun singleChoiceRowsMeetMinimumTouchTargetHeight() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.cd_settings))
            .performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.nav_dns))
            .performClick()

        rule.onNodeWithText(rule.activity.getString(R.string.dns_google))
            .assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText(rule.activity.getString(R.string.dns_custom))
            .assertHeightIsAtLeast(48.dp)
    }

    @Test fun settingsChevronsAreDecorativeOnly() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.cd_settings))
            .performClick()

        rule.onNodeWithText("›", useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
