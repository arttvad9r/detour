package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import kotlinx.coroutines.launch

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
    val settings by store.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val c = detourColors
    val routed = settings?.routes?.countValues { it != AppRoute.DIRECT } ?: 0
    val theme = LocalDetourTheme.current

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(stringResource(R.string.settings_title), onBack)
        Spacer(Modifier.height(Spacing.space8))

        // Логически связанные разделы — одна группа, разделители между строками.
        val items = listOf(
            MenuItem(R.string.nav_routes, { stringResource(R.string.nav_routes_sub, routed) }, R.drawable.ic_routes) to onOpenRoutes,
            MenuItem(R.string.nav_key, { stringResource(R.string.nav_key_sub) }, R.drawable.ic_lock) to onOpenVless,
            MenuItem(R.string.nav_dpi, { stringResource(R.string.nav_dpi_sub) }, R.drawable.ic_dpi) to onOpenDpi,
            MenuItem(R.string.nav_dns, { stringResource(R.string.nav_dns_sub) }, R.drawable.ic_globe) to onOpenDns,
            MenuItem(R.string.nav_backup, { stringResource(R.string.nav_backup_sub) }, R.drawable.ic_export) to onOpenBackup,
            MenuItem(R.string.nav_theme, { theme.label }, R.drawable.ic_theme) to onOpenTheme,
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

        // Автоподключение — отдельная компактная группа.
        Spacer(Modifier.height(Spacing.space12))
        DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { scope.launch { store.setAutoConnect(settings?.autoConnect != true) } }
                    .padding(horizontal = Spacing.space16, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.auto_connect),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                DetourSwitch(
                    checked = settings?.autoConnect == true,
                    onCheckedChange = { v -> scope.launch { store.setAutoConnect(v) } },
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

private fun Map<String, AppRoute>.countValues(pred: (AppRoute) -> Boolean) = values.count(pred)
