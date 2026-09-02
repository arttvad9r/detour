package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R

internal enum class SettingsSection { ROUTES, PROFILES, DPI, DNS, BACKUP, APPEARANCE }

private data class MenuItem(
    val titleRes: Int,
    val sub: @Composable () -> String,
    val iconRes: Int,
    val section: SettingsSection,
)

@Composable
internal fun SettingsMenuScreen(
    viewModel: SettingsMenuViewModel,
    selectedSection: SettingsSection?,
    onOpenRoutes: () -> Unit,
    onOpenVless: () -> Unit,
    onOpenDpi: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenBackup: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val c = detourColors
    val theme = LocalDetourTheme.current
    val scrollState = rememberScrollState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshRoutes()
    }

    val routes = MenuItem(
        R.string.nav_routes,
        { stringResource(R.string.nav_routes_sub, state.routedCount) },
        R.drawable.ic_routes,
        SettingsSection.ROUTES,
    ) to onOpenRoutes
    val profiles = MenuItem(
        R.string.nav_key,
        {
            val configured = buildList {
                if (state.hasVless) add(stringResource(R.string.protocol_vless))
                if (state.hasSubscription) add(stringResource(R.string.subscription_profile_section))
                if (state.hasWarp) add(stringResource(R.string.nav_key_sub_warp))
            }
            configured.takeIf { it.isNotEmpty() }
                ?.joinToString(" + ")
                ?: stringResource(R.string.nav_key_sub_none)
        },
        R.drawable.ic_profile,
        SettingsSection.PROFILES,
    ) to onOpenVless
    val dpi = MenuItem(
        R.string.nav_dpi,
        { stringResource(R.string.nav_dpi_sub) },
        R.drawable.ic_dpi,
        SettingsSection.DPI,
    ) to onOpenDpi
    val dns = MenuItem(
        R.string.nav_dns,
        { stringResource(R.string.nav_dns_sub) },
        R.drawable.ic_globe,
        SettingsSection.DNS,
    ) to onOpenDns
    val backup = MenuItem(
        R.string.nav_backup,
        { stringResource(R.string.nav_backup_sub) },
        R.drawable.ic_export,
        SettingsSection.BACKUP,
    ) to onOpenBackup
    val appearance = MenuItem(
        R.string.nav_theme,
        { stringResource(themeLabel(theme)) },
        R.drawable.ic_theme,
        SettingsSection.APPEARANCE,
    ) to onOpenTheme

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            // Kept only as an accessibility fallback. At normal font scale the
            // compact card fits a standard phone height without scrolling.
            .verticalScroll(scrollState)
            .detourHighRefresh(scrollState.isScrollInProgress),
    ) {
        DetourBrandedHeader(stringResource(R.string.settings_title), onBack)

        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space4))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                SettingsSectionLabel(R.string.settings_section_routing)
                SettingsRows(listOf(routes, profiles), selectedSection)

                SettingsSectionDivider()
                SettingsSectionLabel(R.string.settings_section_connection)
                SettingsRows(listOf(dpi, dns), selectedSection)
                GroupDivider(startInset = 48)
                Row(
                    Modifier.fillMaxWidth()
                        .detourToggleable(
                            value = state.autoConnect,
                            onValueChange = { next ->
                                haptics.performHapticFeedback(
                                    if (next) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                viewModel.setAutoConnect(next)
                            },
                            pressedColor = c.surfaceSelected.copy(alpha = 0.34f),
                            pressScale = Motion.PRESS_ROW,
                        )
                        .heightIn(min = 52.dp)
                        .padding(start = Spacing.space12, end = Spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactSettingsIcon(R.drawable.ic_power)
                    Text(
                        stringResource(R.string.auto_connect),
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textPrimary,
                        modifier = Modifier
                            .padding(start = Spacing.space8)
                            .weight(1f),
                    )
                    DetourSwitch(
                        checked = state.autoConnect,
                        onCheckedChange = null,
                        compact = true,
                    )
                }

                SettingsSectionDivider()
                SettingsSectionLabel(R.string.settings_section_app)
                SettingsRows(listOf(backup, appearance), selectedSection)
            }

            Text(
                stringResource(R.string.autorestart_note),
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = Spacing.space20,
                    end = Spacing.space20,
                    top = Spacing.space4,
                    bottom = Spacing.space8,
                ),
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelMedium,
        color = detourColors.textSecondary,
        modifier = Modifier.padding(
            start = Spacing.space12,
            end = Spacing.space12,
            top = Spacing.space8,
            bottom = Spacing.space4,
        ),
    )
}

@Composable
private fun SettingsSectionDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space12, vertical = Spacing.space2)
            .height(1.dp)
            .background(detourColors.divider),
    )
}

@Composable
private fun SettingsRows(
    items: List<Pair<MenuItem, () -> Unit>>,
    selectedSection: SettingsSection?,
) {
    items.forEachIndexed { index, (item, onClick) ->
        val selected = item.section == selectedSection
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    if (selected) detourColors.accentSoft
                    else androidx.compose.ui.graphics.Color.Transparent,
                )
                .semantics { this.selected = selected },
        ) {
            CompactSettingRow(
                title = stringResource(item.titleRes),
                subtitle = item.sub(),
                iconRes = item.iconRes,
                onClick = onClick,
                selected = selected,
            )
        }
        if (index < items.lastIndex) GroupDivider(startInset = 48)
    }
}

@Composable
private fun CompactSettingRow(
    title: String,
    subtitle: String?,
    iconRes: Int,
    onClick: () -> Unit,
    selected: Boolean,
) {
    val c = detourColors
    Row(
        Modifier
            .fillMaxWidth()
            .detourClickable(
                onClick = onClick,
                role = androidx.compose.ui.semantics.Role.Button,
                pressedColor = c.surfaceSelected.copy(alpha = 0.38f),
                pressScale = Motion.PRESS_ROW,
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = Spacing.space12, vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactSettingsIcon(iconRes)
        Column(
            Modifier
                .padding(start = Spacing.space8)
                .weight(1f),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            SelectionMark(selected = true, modifier = Modifier.padding(end = Spacing.space4))
        } else {
            Chevron()
        }
    }
}

@Composable
private fun CompactSettingsIcon(iconRes: Int) {
    val c = detourColors
    Box(
        Modifier
            .size(30.dp)
            .background(c.accentSoft, AppShapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = c.accent,
            modifier = Modifier.size(15.dp),
        )
    }
}
