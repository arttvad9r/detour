package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R

private data class MenuItem(
    val titleRes: Int,
    val sub: @Composable () -> String,
    val iconRes: Int,
)

@Composable
fun SettingsMenuScreen(
    viewModel: SettingsMenuViewModel,
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

    val items = listOf(
        MenuItem(
            R.string.nav_routes,
            { stringResource(R.string.nav_routes_sub, state.routedCount) },
            R.drawable.ic_routes,
        ) to onOpenRoutes,
        MenuItem(
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
        ) to onOpenVless,
        MenuItem(R.string.nav_dpi, { stringResource(R.string.nav_dpi_sub) }, R.drawable.ic_dpi) to onOpenDpi,
        MenuItem(R.string.nav_dns, { stringResource(R.string.nav_dns_sub) }, R.drawable.ic_globe) to onOpenDns,
        MenuItem(R.string.nav_backup, { stringResource(R.string.nav_backup_sub) }, R.drawable.ic_export) to onOpenBackup,
        MenuItem(R.string.nav_theme, { stringResource(themeLabel(theme)) }, R.drawable.ic_theme) to onOpenTheme,
    )

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .detourHighRefresh(scrollState.isScrollInProgress),
    ) {
        ScreenHeader(stringResource(R.string.settings_title), onBack)

        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space8))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                items.forEachIndexed { i, (item, onClick) ->
                    SettingRow(
                        title = stringResource(item.titleRes),
                        subtitle = item.sub(),
                        iconRes = item.iconRes,
                        onClick = onClick,
                    )
                    if (i < items.lastIndex) GroupDivider()
                }
            }

            Spacer(Modifier.height(Spacing.space12))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
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
                        .heightIn(min = 60.dp)
                        .padding(start = Spacing.space16, end = Spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.auto_connect),
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    DetourSwitch(
                        checked = state.autoConnect,
                        onCheckedChange = null,
                        compact = true,
                    )
                }
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
