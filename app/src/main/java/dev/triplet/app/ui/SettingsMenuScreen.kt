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
import androidx.compose.foundation.layout.width
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
            stringResource(
                when {
                    state.hasVless && state.hasWarp -> R.string.nav_key_sub
                    state.hasVless -> R.string.nav_key_sub_vless
                    state.hasWarp -> R.string.nav_key_sub_warp
                    else -> R.string.nav_key_sub_none
                },
            )
        },
        R.drawable.ic_lock,
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
            .verticalScroll(scrollState)
            .detourHighRefresh(scrollState.isScrollInProgress),
    ) {
        SettingsBrandHeader(onBack)

        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space12))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                SettingsSectionLabel(R.string.settings_section_routing, insideCard = true)
                SettingsRows(listOf(routes, profiles), selectedSection)

                SettingsSectionDivider()
                SettingsSectionLabel(R.string.settings_section_connection, insideCard = true)
                SettingsRows(listOf(dpi, dns), selectedSection)
                GroupDivider()
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
                        .heightIn(min = 64.dp)
                        .padding(start = Spacing.space16, end = Spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetourIconTile(R.drawable.ic_routes)
                    Text(
                        stringResource(R.string.auto_connect),
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary,
                        modifier = Modifier
                            .padding(start = Spacing.space12)
                            .weight(1f),
                    )
                    DetourSwitch(
                        checked = state.autoConnect,
                        onCheckedChange = null,
                        compact = true,
                    )
                }

                SettingsSectionDivider()
                SettingsSectionLabel(R.string.settings_section_app, insideCard = true)
                SettingsRows(listOf(backup, appearance), selectedSection)
            }

            Spacer(Modifier.height(Spacing.space8))
            Text(
                stringResource(R.string.autorestart_note),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                modifier = Modifier.padding(horizontal = Spacing.space20),
            )
            Spacer(Modifier.height(Spacing.space24))
        }
    }
}

@Composable
private fun SettingsBrandHeader(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = Spacing.space4, end = Spacing.space20),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetourIconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.cd_back),
                tint = detourColors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(Spacing.space4))
        DetourBrandMark()
        Spacer(Modifier.width(Spacing.space8))
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            color = detourColors.textPrimary,
        )
    }
}

@Composable
private fun SettingsSectionLabel(titleRes: Int, insideCard: Boolean = false) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = detourColors.textSecondary,
        modifier = Modifier.padding(
            start = Spacing.space16,
            end = Spacing.space16,
            top = if (insideCard) Spacing.space16 else Spacing.space4,
            bottom = Spacing.space8,
        ),
    )
}

@Composable
private fun SettingsSectionDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space16, vertical = Spacing.space4)
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
                    if (selected) detourColors.accentSoft else androidx.compose.ui.graphics.Color.Transparent,
                )
                .semantics { this.selected = selected },
        ) {
            SettingRow(
                title = stringResource(item.titleRes),
                subtitle = item.sub(),
                iconRes = item.iconRes,
                onClick = onClick,
                trailing = if (selected) {
                    { SelectionMark(selected = true, modifier = Modifier.padding(end = Spacing.space8)) }
                } else null,
            )
        }
        if (index < items.lastIndex) GroupDivider()
    }
}
