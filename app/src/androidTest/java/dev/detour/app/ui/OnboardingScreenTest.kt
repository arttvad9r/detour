package dev.detour.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import dev.detour.app.vpn.VpnState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val rule = createComposeRule()
    private var selectedGoal: SetupGoal? = null
    private var connects = 0
    private var finishes = 0

    private fun launch(state: OnboardingState, vpn: VpnState = VpnState.Idle, fontScale: Float = 1f) {
        rule.setContent {
            val theme = AppTheme.DETOUR_LIGHT
            CompositionLocalProvider(LocalDetourTheme provides theme, LocalDetourColors provides theme.colors) {
                MaterialTheme(colorScheme = colorSchemeFor(theme.colors, theme.dark), typography = AppTypography) {
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                        OnboardingScreen(
                            state, SetupCompletion(), vpn, SnackbarHostState(),
                            onChooseGoal = { selectedGoal = it },
                            onOpenProfiles = {}, onOpenRoutes = {}, onOpenDpi = {},
                            onConnect = { connects++ }, onCancelConnect = {},
                            onFinish = { finishes++ }, onSkip = {},
                        )
                    }
                }
            }
        }
    }

    @Test fun lastGoalAndSkipRemainReachableAtTwoHundredPercentFontScale() {
        launch(OnboardingState(), fontScale = 2f)
        rule.onNodeWithTag("setup-goal-WARP").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("setup-skip").assertIsDisplayed()
        rule.runOnIdle { assertEquals(SetupGoal.WARP, selectedGoal) }
    }

    @Test fun anUnconfiguredScenarioCannotConnect() {
        launch(OnboardingState(goal = SetupGoal.VLESS))
        rule.onNodeWithTag("setup-connect").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, connects); assertEquals(0, finishes) }
    }

    @Test fun aFailedConnectionShowsItsReasonAndOffersRetry() {
        launch(OnboardingState(goal = SetupGoal.DPI, canConnect = true), VpnState.Failed("Synthetic failure"))
        rule.onNodeWithText("Synthetic failure").assertIsDisplayed()
        rule.onNodeWithTag("setup-connect").performClick()
        rule.runOnIdle { assertEquals(1, connects); assertEquals(0, finishes) }
    }
}
