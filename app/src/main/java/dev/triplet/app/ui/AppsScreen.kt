package dev.triplet.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.data.AppInfo
import dev.triplet.app.data.AppInventory
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.AppRouteOrdering
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

@Composable
fun AppsScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState(initial = null)
    val pm = ctx.packageManager

    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    // Полный список загружается один раз; сортировка/фильтры — обычный список.
    val allApps = remember { AppInventory.load(ctx) }
    val routes = settings?.routes ?: emptyMap()
    val screenOrder = remember(allApps, settings != null) {
        AppRouteOrdering.snapshot(allApps, routes)
    }
    val apps = remember(allApps, screenOrder, query, showSystem) {
        val ordered = AppRouteOrdering.apply(allApps, screenOrder, routes)
        ordered
            .filter { showSystem || !it.isSystem }
            .filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
    }

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(stringResource(R.string.routes_title), onBack)
        Spacer(Modifier.height(Spacing.space4))

        // Компактный поиск: 48dp, radius 14.
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.space16)
                .height(48.dp)
                .clip(AppShapes.small)
                .background(c.surface)
                .border(1.dp, c.border, AppShapes.small)
                .padding(horizontal = Spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_search), null,
                tint = c.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textMuted,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Системные приложения — компактная строка с переключателем.
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.space20, vertical = Spacing.space8)
                .clickable { showSystem = !showSystem },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.show_system),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            DetourSwitch(checked = showSystem, onCheckedChange = { showSystem = it })
        }

        // Единая поверхность-список: тонкие разделители вместо карточек.
        LazyColumn(Modifier.weight(1f).padding(horizontal = Spacing.space16)) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { i, app ->
                val current = routes[app.packageName] ?: AppRoute.DIRECT
                val shape = when {
                    apps.size == 1 -> AppShapes.small
                    i == 0 -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                    i == apps.lastIndex -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(c.surface, shape),
                ) {
                    AppRow(app, current, pm) { route ->
                        scope.launch {
                            store.setRoute(app.packageName, route)
                            VpnController.restartIfActive(ctx)
                        }
                    }
                    if (i < apps.lastIndex) {
                        Box(
                            Modifier.fillMaxWidth()
                                .padding(start = 52.dp) // 16 + иконка 26 + зазор 10
                                .height(1.dp)
                                .background(c.divider),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.space24)) }
        }
    }
}

/** Строка приложения: иконка, имя, пакет, сегменты маршрута. */
@Composable
private fun AppRow(
    app: AppInfo,
    current: AppRoute,
    pm: android.content.pm.PackageManager,
    onSelect: (AppRoute) -> Unit,
) {
    val c = detourColors
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.space16, vertical = Spacing.space12)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val bmp = remember(app.packageName) {
                runCatching { pm.getApplicationIcon(app.packageName).toBitmap(48, 48) }.getOrNull()
            }
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), null,
                    modifier = Modifier.size(26.dp).clip(AppShapes.extraSmall),
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_routes), null,
                    modifier = Modifier.size(22.dp),
                    tint = c.textMuted,
                )
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SegmentedControl(
            options = AppRoute.entries.map { stringResource(routeLabel(it)) },
            selected = AppRoute.entries.indexOf(current),
            onSelect = { idx -> onSelect(AppRoute.entries[idx]) },
        )
    }
}

fun routeLabel(r: AppRoute): Int = when (r) {
    AppRoute.DIRECT -> R.string.route_direct
    AppRoute.VPN -> R.string.route_vpn
    AppRoute.DPI -> R.string.route_dpi
}
