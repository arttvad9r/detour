package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R

internal enum class SettingsSection {
    ROUTES,
    DESTINATION_RULES,
    PROFILES,
    DPI,
    DNS,
    DIAGNOSTICS,
    BACKUP,
    APPEARANCE,
}

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
    onOpenDestinationRules: () -> Unit,
    onOpenVless: () -> Unit,
    onOpenDpi: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAlwaysOnVpnSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val c = detourColors
    val theme = LocalDetourTheme.current
    val scrollState = rememberScrollState()
    var showAlwaysOnDialog by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshRoutes()
    }

    val routes = MenuItem(
        R.string.nav_routes,
        { stringResource(R.string.nav_routes_sub, state.routedCount) },
        R.drawable.ic_routes,
        SettingsSection.ROUTES,
    ) to onOpenRoutes
    val destinationRules = MenuItem(
        R.string.nav_destination_rules,
        { stringResource(R.string.nav_destination_rules_sub) },
        R.drawable.ic_globe,
        SettingsSection.DESTINATION_RULES,
    ) to onOpenDestinationRules
    val profiles = MenuItem(
        R.string.nav_key,
        {
            val configured = buildList {
                // A subscription is a source of VLESS nodes, not a separate VPN
                // protocol. Keep the Settings summary protocol-oriented while the
                // Profiles screen can still separate direct links/subscriptions.
                if (state.hasVless || state.hasSubscription) {
                    add(stringResource(R.string.protocol_vless))
                }
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
    val diagnostics = MenuItem(
        R.string.nav_diagnostics,
        { stringResource(R.string.nav_diagnostics_sub) },
        R.drawable.ic_check,
        SettingsSection.DIAGNOSTICS,
    ) to onOpenDiagnostics
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
        DetourBrandedHeader(stringResource(R.string.settings_title), onBack)

        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space4))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                SettingsSectionLabel(R.string.settings_section_routing)
                SettingsRows(listOf(routes, destinationRules, profiles), selectedSection)

                SettingsSectionDivider()
                SettingsSectionLabel(R.string.settings_section_connection)
                SettingsRows(listOf(dpi, dns), selectedSection)
                GroupDivider(startInset = NavigationRowDividerInset)
                DetourNavigationRow(
                    title = stringResource(R.string.auto_connect),
                    subtitle = null,
                    iconRes = R.drawable.ic_power,
                    modifier = Modifier.detourToggleable(
                        value = state.autoConnect,
                        onValueChange = { next ->
                            haptics.performHapticFeedback(
                                if (next) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                            viewModel.setAutoConnect(next)
                        },
                        pressedColor = c.surfaceSelected.copy(alpha = 0.34f),
                        pressScale = Motion.PRESS_ROW,
                    ),
                    trailing = {
                        DetourSwitch(
                            checked = state.autoConnect,
                            onCheckedChange = null,
                            compact = true,
                        )
                    },
                )
                GroupDivider(startInset = NavigationRowDividerInset)
                DetourNavigationRow(
                    title = stringResource(R.string.always_on_vpn),
                    subtitle = stringResource(R.string.always_on_vpn_sub),
                    iconRes = R.drawable.ic_lock,
                    onClick = { showAlwaysOnDialog = true },
                )

                SettingsSectionDivider()
                SettingsSectionLabel(R.string.settings_section_app)
                SettingsRows(listOf(diagnostics, backup, appearance), selectedSection)
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

    if (showAlwaysOnDialog) {
        AlwaysOnVpnDialog(
            onOpenSettings = {
                showAlwaysOnDialog = false
                onOpenAlwaysOnVpnSettings()
            },
            onDismiss = { showAlwaysOnDialog = false },
        )
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
        DetourNavigationRow(
            title = stringResource(item.titleRes),
            subtitle = item.sub(),
            iconRes = item.iconRes,
            onClick = onClick,
            selectedBackground = selected,
            modifier = Modifier.semantics { this.selected = selected },
        )
        if (index < items.lastIndex) GroupDivider(startInset = NavigationRowDividerInset)
    }
}
