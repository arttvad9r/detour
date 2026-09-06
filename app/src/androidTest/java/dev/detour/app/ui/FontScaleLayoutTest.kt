package dev.detour.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.detour.app.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FontScaleLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Test fun settingRowGrowsAtTwoHundredPercentFontScale() {
        rule.setContent {
            TestTheme {
                Box {
                    TaggedSettingRow("setting-normal")
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                        TaggedSettingRow("setting-large")
                    }
                }
            }
        }

        assertGrows("setting-normal", "setting-large")
    }

    @Test fun navigationRowGrowsAtTwoHundredPercentFontScale() {
        rule.setContent {
            TestTheme {
                Box {
                    TaggedNavigationRow("navigation-normal")
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                        TaggedNavigationRow("navigation-large")
                    }
                }
            }
        }

        assertGrows("navigation-normal", "navigation-large")
    }

    @Test fun choiceRowGrowsAtTwoHundredPercentFontScale() {
        rule.setContent {
            TestTheme {
                Box {
                    TaggedChoiceRow("choice-normal")
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                        TaggedChoiceRow("choice-large")
                    }
                }
            }
        }

        assertGrows("choice-normal", "choice-large")
    }

    @Test fun passiveCompactSwitchDoesNotInflateNavigationRow() {
        rule.setContent {
            TestTheme {
                Box {
                    TaggedNavigationRow("navigation-plain")
                    TaggedToggleNavigationRow("navigation-toggle")
                }
            }
        }

        rule.waitForIdle()
        val plain = rule.onNodeWithTag("navigation-plain").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.height
        val toggle = rule.onNodeWithTag("navigation-toggle").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.height

        assertTrue("Expected toggle row ($toggle px) to match navigation row ($plain px)", toggle == plain)
    }

    @Test fun segmentedControlGrowsAtTwoHundredPercentFontScale() {
        rule.setContent {
            TestTheme {
                Box {
                    TaggedSegmentedControl("segments-normal")
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                        TaggedSegmentedControl("segments-large")
                    }
                }
            }
        }

        assertGrows("segments-normal", "segments-large")
    }

    @Test fun singleLineInputGrowsAtTwoHundredPercentFontScale() {
        rule.setContent {
            TestTheme {
                Box {
                    TaggedInput("input-normal")
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                        TaggedInput("input-large")
                    }
                }
            }
        }

        assertGrows("input-normal", "input-large")
    }

    @Test fun settingsDetailEmptyStateDisplaysAtTwoHundredPercentFontScale() {
        rule.setContent {
            TestTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                    SettingsDetailEmptyState(
                        Modifier.testTag("settings-detail-large"),
                    )
                }
            }
        }

        rule.onNodeWithTag("settings-detail-large").assertIsDisplayed()
    }

    private fun assertGrows(normalTag: String, largeTag: String) {
        rule.waitForIdle()
        val normal = rule.onNodeWithTag(normalTag).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.height
        val large = rule.onNodeWithTag(largeTag).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.height

        assertTrue("Expected $largeTag ($large px) to grow beyond $normalTag ($normal px)", large > normal)
    }
}

@Composable
private fun TaggedSettingRow(tag: String) {
    Box(Modifier.width(320.dp).testTag(tag)) {
        SettingRow(
            title = "Applications routed through VPN",
            subtitle = "Selected routes and exclusions remain visible at large text sizes",
            iconRes = R.drawable.ic_routes,
            onClick = {},
        )
    }
}

@Composable
private fun TaggedNavigationRow(tag: String) {
    Box(Modifier.width(320.dp).testTag(tag)) {
        DetourNavigationRow(
            title = "Applications routed through VPN",
            subtitle = "Selected routes and exclusions remain visible at large text sizes",
            iconRes = R.drawable.ic_routes,
            onClick = {},
        )
    }
}

@Composable
private fun TaggedToggleNavigationRow(tag: String) {
    Box(Modifier.width(320.dp).testTag(tag)) {
        DetourNavigationRow(
            title = "Connect automatically",
            subtitle = null,
            iconRes = R.drawable.ic_power,
            modifier = Modifier.detourToggleable(
                value = true,
                onValueChange = {},
            ),
            trailing = {
                DetourSwitch(
                    checked = true,
                    onCheckedChange = null,
                    compact = true,
                )
            },
        )
    }
}

@Composable
private fun TaggedChoiceRow(tag: String) {
    Box(Modifier.width(320.dp).testTag(tag)) {
        ChoiceRow(
            title = "Use the recommended connection profile",
            subtitle = "This description must remain readable when the system font is enlarged",
            selected = true,
            onClick = {},
        )
    }
}

@Composable
private fun TaggedSegmentedControl(tag: String) {
    SegmentedControl(
        options = listOf("Recommended routing mode", "Custom routing mode"),
        selected = 0,
        onSelect = {},
        modifier = Modifier.width(320.dp).testTag(tag),
    )
}

@Composable
private fun TaggedInput(tag: String) {
    DetourInputField(
        value = "https://dns.example/dns-query",
        onValueChange = {},
        label = "Custom DNS resolver endpoint",
        placeholder = "https://example/dns-query",
        helper = "HTTPS resolver address used by the tunnel",
        modifier = Modifier.width(320.dp).testTag(tag),
    )
}

@Composable
private fun TestTheme(content: @Composable () -> Unit) {
    val theme = AppTheme.CATPPUCCIN_LATTE
    CompositionLocalProvider(
        LocalDetourTheme provides theme,
        LocalDetourColors provides theme.colors,
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(theme.colors, theme.dark),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
