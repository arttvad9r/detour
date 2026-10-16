package dev.detour.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.detour.app.R
import dev.detour.app.core.AppRoute
import dev.detour.app.vpn.VpnState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppRouteDetailScreenTest {
    @get:Rule val rule = createComposeRule()
    private var profiles = 0
    private var backs = 0
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun launch(readiness: RouteReadiness, vpn: VpnState, font: Float = 1f, dark: Boolean = false) {
        rule.setContent {
            val theme = if (dark) AppTheme.DETOUR_DARK else AppTheme.DETOUR_LIGHT
            CompositionLocalProvider(LocalDetourTheme provides theme, LocalDetourColors provides theme.colors) {
                MaterialTheme(colorScheme = colorSchemeFor(theme.colors, theme.dark), typography = AppTypography) {
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(font)) {
                        AppRouteDetailScreen("example.video", "Example Video", AppRoute.DPI, readiness, vpn,
                            onBack = { backs++ }, onOpenProfiles = { profiles++ }, onOpenDpi = {},
                            onOpenConnection = {}, onRetry = {})
                    }
                }
            }
        }
    }

    @Test fun missingProfileActionRemainsReachableAtLargeFont() {
        launch(RouteReadiness.MISSING_PROFILE, VpnState.Idle, 2f)
        rule.onNodeWithText(context.getString(R.string.route_detail_profiles)).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, profiles) }
        rule.onNodeWithText(context.getString(R.string.route_detail_edit)).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun activeConnectionStillExplainsThatAssignmentsAreNotLiveTraffic() {
        launch(RouteReadiness.CONFIGURED, VpnState.Active, dark = true)
        rule.onNodeWithText(context.getString(R.string.route_detail_not_live)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.route_detail_active)).performScrollTo().assertIsDisplayed()
    }
}
