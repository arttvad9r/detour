package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val routed = settings?.routes?.countValues { it != AppRoute.DIRECT } ?: 0
    val theme = AppTheme.byId(settings?.themeId ?: "")

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(stringResource(R.string.settings_title), onBack)
        Spacer(Modifier.height(6.dp))

        // Логически связанные разделы — одна карточка, разделители между строками.
        val items = listOf(
            MenuItem(R.string.nav_routes, { stringResource(R.string.nav_routes_sub, routed) }, R.drawable.ic_routes) to onOpenRoutes,
            MenuItem(R.string.nav_key, { stringResource(R.string.nav_key_sub) }, R.drawable.ic_lock) to onOpenVless,
            MenuItem(R.string.nav_dpi, { stringResource(R.string.nav_dpi_sub) }, R.drawable.ic_dpi) to onOpenDpi,
            MenuItem(R.string.nav_dns, { stringResource(R.string.nav_dns_sub) }, R.drawable.ic_globe) to onOpenDns,
            MenuItem(R.string.nav_backup, { stringResource(R.string.nav_backup_sub) }, R.drawable.ic_export) to onOpenBackup,
            MenuItem(R.string.nav_theme, { theme.label }, R.drawable.ic_theme) to onOpenTheme,
        )
        DetourCard(Modifier.padding(horizontal = 16.dp)) {
            items.forEachIndexed { i, (item, onClick) ->
                SettingRow(
                    title = stringResource(item.titleRes),
                    subtitle = item.sub(settings),
                    iconRes = item.iconRes,
                    onClick = onClick,
                )
                if (i < items.lastIndex) CardDivider()
            }
        }

        Spacer(Modifier.height(12.dp))
        DetourCard(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.auto_connect), fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = settings?.autoConnect == true, onCheckedChange = { v ->
                    scope.launch { store.setAutoConnect(v) }
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.autorestart_note),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
}

private fun Map<String, AppRoute>.countValues(pred: (AppRoute) -> Boolean) = values.count(pred)

/** Общий заголовок внутренних экранов: назад + название. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 6.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.cd_back),
                tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(2.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
    }
}
