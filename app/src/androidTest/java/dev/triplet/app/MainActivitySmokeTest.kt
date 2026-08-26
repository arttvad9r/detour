package dev.triplet.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the real activity against default settings and checks primary controls. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test fun homeShowsConnectControl() {
        rule.onNodeWithText(rule.activity.getString(R.string.btn_connect)).assertIsDisplayed()
    }

    @Test fun homeShowsSettingsEntry() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.cd_settings))
            .assertIsDisplayed()
    }
}
