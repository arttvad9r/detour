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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.delay

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

    val protocolRes = when (state.protocol) {
        HomeProtocol.VLESS_DPI -> if (state.activeVpn == VpnProfileKind.WARP) R.string.protocol_warp_dpi else R.string.protocol_vless_dpi
        HomeProtocol.DPI -> R.string.protocol_dpi
        HomeProtocol.VLESS -> if (state.activeVpn == VpnProfileKind.WARP) R.string.protocol_warp else R.string.protocol_vless
        HomeProtocol.NONE -> R.string.protocol_none
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
            .verticalScroll(scrollState)
            .detourHighRefresh(scrollState.isScrollInProgress),
    ) {
        DetourBrandedHeader(stringResource(R.string.settings_title), onBack)

        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space12))
            SettingsConnectionSummary(
                vpnState = state.vpnState,
                sessionStartedAt = state.sessionStartedAt,
                protocol = stringResource(protocolRes),
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )

            Spacer(Modifier.height(Spacing.space16))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                SettingsSectionLabel(R.string.settings_section_routing)
                SettingsRows(listOf(routes, profiles), selectedSection)

                SettingsSectionDivider()
                SettingsSectionLabel(R.string.settings_section_connection)
                SettingsRows(listOf(dpi, dns), selectedSection)
                GroupDivider(startInset = 16)
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
                        .heightIn(min = 72.dp)
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetourIconTile(R.drawable.ic_power)
                    Text(
                        stringResource(R.string.auto_connect),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary,
                        modifier = Modifier
                            .padding(start = Spacing.space16)
                            .weight(1f),
                    )
                    DetourSwitch(
                        checked = state.autoConnect,
                        onCheckedChange = null,
                        compact = false,
                    )
                }

                SettingsSectionDivider()
                SettingsSectionLabel(R.string.settings_section_app)
                SettingsRows(listOf(backup, appearance), selectedSection)
            }

            Spacer(Modifier.height(Spacing.space12))
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
private fun SettingsConnectionSummary(
    vpnState: VpnState,
    sessionStartedAt: Long?,
    protocol: String,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val style = statusStyleFor(c, vpnState)
    val titleRes = when (vpnState) {
        VpnState.Active -> R.string.status_active
        VpnState.Starting -> R.string.status_starting
        is VpnState.Failed -> R.string.status_failed
        VpnState.Idle -> R.string.status_idle
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(style.container, AppShapes.medium)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(style.content.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (vpnState) {
                VpnState.Starting -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = style.content,
                )
                VpnState.Active -> Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = style.content,
                    modifier = Modifier.size(24.dp),
                )
                is VpnState.Failed,
                VpnState.Idle,
                -> Box(
                    Modifier
                        .size(10.dp)
                        .background(style.content, CircleShape),
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(start = Spacing.space16)
                .weight(1f),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (vpnState is VpnState.Failed) c.error else c.textPrimary,
            )
            Text(
                text = protocol,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Spacing.space2),
            )
        }

        if (vpnState == VpnState.Active) {
            SettingsSessionTimer(sessionStartedAt)
        }
    }
}

@Composable
private fun SettingsSessionTimer(sessionStartedAt: Long?) {
    var elapsed by remember(sessionStartedAt) { mutableIntStateOf(0) }
    LaunchedEffect(sessionStartedAt) {
        val started = sessionStartedAt ?: System.currentTimeMillis()
        while (true) {
            elapsed = ((System.currentTimeMillis() - started) / 1000L).coerceAtLeast(0).toInt()
            delay(1000)
        }
    }
    Text(
        text = formatSessionElapsed(elapsed),
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
        ),
        color = detourColors.textSecondary,
    )
}

@Composable
private fun SettingsSectionLabel(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = detourColors.textSecondary,
        modifier = Modifier.padding(
            start = Spacing.space16,
            end = Spacing.space16,
            top = Spacing.space16,
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
        SettingsMenuRow(
            title = stringResource(item.titleRes),
            subtitle = item.sub(),
            iconRes = item.iconRes,
            selected = selected,
            onClick = onClick,
        )
        if (index < items.lastIndex) GroupDivider(startInset = 78)
    }
}

@Composable
private fun SettingsMenuRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = detourColors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .semantics { this.selected = selected }
            .detourClickable(
                onClick = onClick,
                role = Role.Button,
                pressedColor = c.surfaceSelected.copy(alpha = 0.38f),
                pressScale = Motion.PRESS_ROW,
            )
            .heightIn(min = 72.dp)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetourIconTile(iconRes = iconRes, selected = selected)
        Column(
            modifier = Modifier
                .padding(start = Spacing.space16)
                .weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Spacing.space2),
            )
        }
        if (selected) {
            SelectionMark(selected = true, modifier = Modifier.padding(end = Spacing.space8))
        } else {
            Chevron()
        }
    }
}
