package dev.triplet.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AdaptiveLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun contentUsesFullWidthOnCompactWindow() {
        rule.setContent {
            Box(Modifier.width(360.dp).testTag("root")) {
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

        rule.onNodeWithTag("root").assertWidthIsEqualTo(360.dp)
        rule.onNodeWithTag("content").assertWidthIsEqualTo(360.dp)
    }

    @Test
    fun contentIsCappedAndCenteredOnExpandedWindow() {
        rule.setContent {
            Box(Modifier.width(1000.dp).testTag("root")) {
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

        rule.onNodeWithTag("content").assertWidthIsEqualTo(640.dp)
        val rootBounds = rule.onNodeWithTag("root").fetchSemanticsNode().boundsInRoot
        val contentBounds = rule.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        assertEquals(rootBounds.center.x, contentBounds.center.x, 0.5f)
    }
}
