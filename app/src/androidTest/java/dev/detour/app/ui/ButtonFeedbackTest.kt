package dev.detour.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ButtonFeedbackTest {
    @get:Rule val rule = createComposeRule()
    private var clicks = 0
    private val feedback = mutableListOf<HapticFeedbackType>()

    private fun content(enabled: Boolean = true) {
        rule.setContent {
            val theme = AppTheme.DETOUR_DARK
            CompositionLocalProvider(
                LocalDetourTheme provides theme,
                LocalDetourColors provides theme.colors,
                LocalHapticFeedback provides object : HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                        feedback.add(hapticFeedbackType)
                    }
                },
            ) {
                MaterialTheme(colorScheme = colorSchemeFor(theme.colors, theme.dark), typography = AppTypography) {
                    DetourButton("Connect", { clicks++ }, enabled = enabled, hapticFeedback = HapticFeedbackType.SegmentTick)
                }
            }
        }
    }

    @Test fun feedbackOccursOnceOnReleaseAndCancelledPressDoesNothing() {
        content()
        rule.onNodeWithText("Connect").performTouchInput { down(center) }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(0, clicks)
            assertEquals(emptyList<HapticFeedbackType>(), feedback)
        }
        rule.onNodeWithText("Connect").performTouchInput { cancel() }
        rule.runOnIdle { assertEquals(0, clicks); assertEquals(0, feedback.size) }
        rule.onNodeWithText("Connect").performTouchInput { down(center); up() }
        rule.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(listOf(HapticFeedbackType.SegmentTick), feedback)
        }
    }

    @Test fun disabledButtonDoesNotDispatchActionOrHaptics() {
        content(enabled = false)
        rule.onNodeWithText("Connect").performTouchInput { down(center); up() }
        rule.runOnIdle { assertEquals(0, clicks); assertEquals(0, feedback.size) }
    }
}
