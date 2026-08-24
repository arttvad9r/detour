package dev.triplet.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.data.AppInventory
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

@Composable
fun AppsScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = null)
    val pm = ctx.packageManager

    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    // Полный список загружается один раз; фильтры — мгновенные, без переquery.
    val allApps = remember { AppInventory.load(ctx).sortedBy { it.label.lowercase() } }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.routes_title), onBack)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.show_system), fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground)
            Switch(checked = showSystem, onCheckedChange = { showSystem = it })
        }

        val routes = settings?.routes
        LazyColumn(Modifier.weight(1f)) {
            allApps
                .filter { showSystem || !it.isSystem }
                .filter {
                    query.isBlank() ||
                        it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                }
                // маршрутизированные (не «Напрямую») — вверху списка
                .sortedWith(compareBy(
                    { (routes?.get(it.packageName) ?: AppRoute.DIRECT) == AppRoute.DIRECT },
                    { it.label.lowercase() },
                ))
                .forEach { app ->
                    item(key = app.packageName) {
                        val current = routes?.get(app.packageName) ?: AppRoute.DIRECT
                        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 5.dp)
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, top = 11.dp, bottom = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val bmp = remember(app.packageName) {
                                    runCatching { pm.getApplicationIcon(app.packageName).toBitmap(48, 48) }.getOrNull()
                                }
                                if (bmp != null) {
                                    Image(bmp.asImageBitmap(), null,
                                        modifier = Modifier.size(34.dp))
                                } else {
                                    Icon(painterResource(R.drawable.ic_routes), null,
                                        modifier = Modifier.size(30.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                                Column(Modifier.padding(start = 11.dp)) {
                                    Text(app.label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text(app.packageName, fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // Кнопки отдельной строкой на всю ширину: на узких экранах
                            // и в RU они не помещаются рядом с названием.
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                                AppRoute.entries.forEachIndexed { i, route ->
                                    SegmentedButton(
                                        selected = current == route,
                                        onClick = {
                                            scope.launch {
                                                store.setRoute(app.packageName, route)
                                                VpnController.restartIfActive(ctx)
                                            }
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(i, AppRoute.entries.size),
                                        label = {
                                            Text(stringResource(routeLabel(route)), fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}

fun routeLabel(r: AppRoute): Int = when (r) {
    AppRoute.DIRECT -> R.string.route_direct
    AppRoute.VPN -> R.string.route_vpn
    AppRoute.DPI -> R.string.route_dpi
}
