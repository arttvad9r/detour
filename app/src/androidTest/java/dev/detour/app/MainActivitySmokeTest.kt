package dev.detour.app

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the real activity against default settings and checks primary controls. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before fun skipFirstRunForExistingScreenChecks() {
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("setup-skip").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.cd_settings))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithTag("setup-skip").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag("setup-skip").performClick()
        }
    }

    @Test fun homeShowsConnectControl() {
        rule.onNodeWithText(rule.activity.getString(R.string.btn_connect)).assertIsDisplayed()
    }

    @Test fun homeShowsSettingsEntry() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.cd_settings))
            .assertIsDisplayed()
    }

    @Test fun dnsDetailSurvivesActivityRecreation() {
        val settings = rule.activity.getString(R.string.cd_settings)
        val dns = rule.activity.getString(R.string.nav_dns)
        val customDns = rule.activity.getString(R.string.dns_custom)

        rule.onNodeWithContentDescription(settings).performClick()
        rule.onNodeWithText(dns).performClick()
        rule.onNodeWithText(customDns).assertIsDisplayed()

        rule.activityRule.scenario.recreate()

        rule.onNodeWithText(customDns).assertIsDisplayed()
    }

    @Test fun mainActivityResizesForIme() {
        val adjustMode = rule.activity.window.attributes.softInputMode and
            WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, adjustMode)
    }
}
