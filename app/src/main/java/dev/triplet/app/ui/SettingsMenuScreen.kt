package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import dev.triplet.app.R
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import dev.triplet.app.vpn.EffectiveRoutes
import dev.triplet.app.vpn.resolveEffectiveRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class MenuItem(
    val titleRes: Int,
    val sub: @Composable (TriSettings?) -> String,
    val iconRes: Int,
)

@Composable
fun SettingsMenuScreen(
    store: RoutesStore,
    onOpenRoutes: () -> Unit,
    onOpenVless: () -> Unit,
    onOpenDpi: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenBackup: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val settings by store.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val c = detourColors
    val persistedRoutes = settings?.routes.orEmpty()
    var routeRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(ctx) {
        val owner = ctx as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) routeRevision++
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val effectiveRoutes by produceState(
        initialValue = EffectiveRoutes(emptySet(), emptySet()),
        key1 = persistedRoutes,
        key2 = routeRevision,
    ) {
        value = if (persistedRoutes.isEmpty()) {
            EffectiveRoutes(emptySet(), emptySet())
        } else {
            withContext(Dispatchers.IO) {
                resolveEffectiveRoutes(ctx.packageManager, persistedRoutes)
            }
        }
    }
    val routed = effectiveRoutes.packages.size
    val theme = LocalDetourTheme.current
    val autoConnect = settings?.autoConnect == true

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(stringResource(R.string.settings_title), onBack)
        Spacer(Modifier.height(Spacing.space8))

        val items = listOf(
            MenuItem(R.string.nav_routes, { stringResource(R.string.nav_routes_sub, routed) }, R.drawable.ic_routes) to onOpenRoutes,
            MenuItem(
                R.string.nav_key,
                { current ->
                    val hasVless = current?.vlessKeys?.items?.isNotEmpty() == true
                    val hasWarp = current?.warpProfile != null
                    stringResource(
                        when {
                            hasVless && hasWarp -> R.string.nav_key_sub
                            hasVless -> R.string.nav_key_sub_vless
                            hasWarp -> R.string.nav_key_sub_warp
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
        DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
            items.forEachIndexed { i, (item, onClick) ->
                SettingRow(
                    title = stringResource(item.titleRes),
                    subtitle = item.sub(settings),
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
                        value = autoConnect,
                        onValueChange = { next ->
                            haptics.performHapticFeedback(
                                if (next) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                            scope.launch { store.setAutoConnect(next) }
                        },
                        pressedColor = c.surfaceSelected.copy(alpha = 0.34f),
                        pressScale = Motion.PRESS_ROW,
                    )
                    .height(60.dp)
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
                    checked = autoConnect,
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
