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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.data.RoutesStore

private data class MenuItem(val titleRes: Int, val subRes: Int, val iconRes: Int, val tint: Color)

@Composable
fun SettingsMenuScreen(
    store: RoutesStore,
    onOpenRoutes: () -> Unit,
    onOpenVless: () -> Unit,
    onOpenDpi: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by store.settings.collectAsState(initial = null)
    val routed = settings?.routes?.countValues { it != AppRoute.DIRECT } ?: 0

    Column(modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.settings_title), onBack)

        val items = listOf(
            MenuItem(R.string.nav_routes, 0, R.drawable.ic_routes, Color(0xFF4C6EF5)) to onOpenRoutes,
            MenuItem(R.string.nav_key, R.string.nav_key_sub, R.drawable.ic_lock, Color(0xFF1F9D5A)) to onOpenVless,
            MenuItem(R.string.nav_dpi, R.string.nav_dpi_sub, R.drawable.ic_dpi, Color(0xFF34507B)) to onOpenDpi,
        )
        items.forEach { (item, onClick) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 5.dp)
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                    .clickable(onClick = onClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(42.dp).background(item.tint.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(item.iconRes), null, tint = item.tint, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(stringResource(item.titleRes), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (item.titleRes == R.string.nav_routes) stringResource(R.string.nav_routes_sub, routed)
                        else stringResource(item.subRes),
                        fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.autorestart_note),
            fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

private fun Map<String, AppRoute>.countValues(pred: (AppRoute) -> Boolean) = values.count(pred)

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 6.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.cd_back),
                tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.size(width = 2.dp, height = 0.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground)
    }
}
