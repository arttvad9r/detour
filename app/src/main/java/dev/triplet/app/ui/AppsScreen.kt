package dev.triplet.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.data.AppInventory
import dev.triplet.app.data.RoutesMapping
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

@Composable
fun AppsScreen(store: RoutesStore, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = null)
    var query by remember { mutableStateOf("") }
    val apps = remember { AppInventory.load(ctx) }
    val filtered = remember(query, apps) {
        RoutesMapping.sortApps(apps).filter {
            it.label.contains(query, true) || it.packageName.contains(query, true)
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.apps_search)) },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(filtered, key = { it.packageName }) { app ->
                val current = settings?.routes?.get(app.packageName) ?: AppRoute.DIRECT
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    // Кнопки отдельной строкой на всю ширину: в одной строке с
                    // названием на узких экранах и в RU они не помещаются.
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        AppRoute.entries.forEach { route ->
                            SegmentedButton(
                                selected = current == route,
                                onClick = {
                                    scope.launch {
                                        store.setRoute(app.packageName, route)
                                        VpnController.restartIfActive(ctx)
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = route.ordinal, count = AppRoute.entries.size),
                                label = {
                                    Text(short(route, ctx), style = MaterialTheme.typography.labelSmall)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun short(route: AppRoute, ctx: android.content.Context) = when (route) {
    AppRoute.DIRECT -> ctx.getString(R.string.route_direct)
    AppRoute.VPN -> ctx.getString(R.string.route_vpn)
    AppRoute.DPI -> ctx.getString(R.string.route_dpi)
}
