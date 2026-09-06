package dev.detour.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class AdaptiveLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun contentUsesFullWidthWhenParentIsBelowMaxWidth() {
        rule.setContent {
            Box(Modifier.width(200.dp).testTag("root")) {
                DetourContentColumn {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .testTag("content"),
                    )
                }
            }
        }

        rule.onNodeWithTag("root").assertWidthIsEqualTo(200.dp)
        rule.onNodeWithTag("content").assertWidthIsEqualTo(200.dp)
        rule.onNodeWithTag("content").assertLeftPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun contentIsCappedAndCenteredWhenMaxWidthIsBelowParentWidth() {
        rule.setContent {
            Box(Modifier.width(200.dp).testTag("root")) {
                DetourContentColumn(maxWidth = 120.dp) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .testTag("content"),
                    )
                }
            }
        }

        rule.onNodeWithTag("root").assertWidthIsEqualTo(200.dp)
        rule.onNodeWithTag("content").assertWidthIsEqualTo(120.dp)
        rule.onNodeWithTag("content").assertLeftPositionInRootIsEqualTo(40.dp)
    }
}
