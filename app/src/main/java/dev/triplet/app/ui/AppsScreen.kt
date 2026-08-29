package dev.triplet.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.data.AppInfo
import dev.triplet.app.data.AppInventory
import dev.triplet.app.data.AppRouteOrdering
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppsScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState()
    val currentSettings = settings ?: return
    val searchHint = stringResource(R.string.search_hint)

    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var inventoryRevision by remember { mutableIntStateOf(0) }

    // Re-query PackageManager whenever Detour comes back to the foreground. This
    // catches apps installed/removed while Play Store or another installer was on
    // top. AppInventory keeps the previous snapshot renderable until refresh ends.
    DisposableEffect(ctx) {
        val owner = ctx as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) inventoryRevision++
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    val searchBorder by animateColorAsState(
        if (searchFocused) c.accent else c.border,
        tween(Motion.COLOR_MS), label = "searchBorder",
    )
    val searchIcon by animateColorAsState(
        if (searchFocused) c.accent else c.textMuted,
        tween(Motion.COLOR_MS), label = "searchIcon",
    )
    val showSystem = currentSettings.showSystemApps
    val allApps by produceState<List<AppInfo>?>(
        initialValue = AppInventory.peek(),
        key1 = ctx,
        key2 = inventoryRevision,
    ) {
        value = withContext(Dispatchers.IO) { AppInventory.load(ctx) }
    }
    val routes = currentSettings.routes
    val loadedApps = allApps.orEmpty()
    val screenOrder = remember(loadedApps) {
        AppRouteOrdering.snapshot(loadedApps, currentSettings.routes)
    }
    val apps = remember(loadedApps, screenOrder, query, showSystem) {
        val ordered = AppRouteOrdering.apply(loadedApps, screenOrder)
        ordered
            .filter { showSystem || !it.isSystem }
            .filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
    }
    val appKeys = remember(apps) { apps.map { it.packageName } }
    val listState = rememberLazyListState()
    var listMotionActive by remember { mutableStateOf(false) }
    LaunchedEffect(query, showSystem, appKeys) {
        listMotionActive = true
        delay(Motion.LIST_REFRESH_BOOST_MS)
        listMotionActive = false
    }

    fun setShowSystemFromRow(value: Boolean) {
        haptics.performHapticFeedback(
            if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
        scope.launch { store.setShowSystemApps(value) }
    }

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(stringResource(R.string.routes_title), onBack)
        Spacer(Modifier.height(Spacing.space4))

        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.space16)
                .height(56.dp)
                .clip(AppShapes.small)
                .background(c.surface)
                .border(1.dp, searchBorder, AppShapes.small)
                .padding(horizontal = Spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_search), null,
                tint = searchIcon,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = query.isEmpty(),
                    enter = fadeIn(tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING)),
                    exit = fadeOut(tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING)),
                ) {
                    Text(
                        searchHint,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { searchFocused = it.isFocused }
                        .semantics { contentDescription = searchHint },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.space16, vertical = Spacing.space8)
                .detourToggleable(
                    value = showSystem,
                    onValueChange = ::setShowSystemFromRow,
                    pressedColor = c.surfaceSelected.copy(alpha = 0.32f),
                    pressScale = Motion.PRESS_ROW,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.show_system),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            DetourSwitch(
                checked = showSystem,
                onCheckedChange = null,
            )
        }

        DetourCard(Modifier.weight(1f).padding(horizontal = Spacing.space16)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .detourHighRefresh(listState.isScrollInProgress || listMotionActive),
            ) {
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
                            .animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = spring(
                                    dampingRatio = Motion.SPRING_DAMPING,
                                    stiffness = Motion.SPRING_STIFFNESS_SOFT,
                                ),
                            )
                            .fillMaxWidth()
                            .clip(shape),
                    ) {
                        AppRow(app, current) { route ->
                            scope.launch {
                                store.setRoute(app.packageName, route)
                                VpnController.restartIfActive(ctx)
                            }
                        }
                        if (i < apps.lastIndex) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .padding(start = 52.dp)
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
}

@Composable
private fun AppRow(
    app: AppInfo,
    current: AppRoute,
    onSelect: (AppRoute) -> Unit,
) {
    val ctx = LocalContext.current
    val c = detourColors
    val bmp by produceState<Bitmap?>(
        initialValue = AppInventory.peekIcon(app.packageName),
        key1 = app.packageName,
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                AppInventory.loadIcon(ctx, app.packageName)
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.space16, vertical = Spacing.space12)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = bmp
            if (icon != null) {
                Image(
                    icon.asImageBitmap(), null,
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
            modifier = Modifier.height(32.dp),
        )
    }
}

fun routeLabel(r: AppRoute): Int = when (r) {
    AppRoute.DIRECT -> R.string.route_direct
    AppRoute.VPN -> R.string.route_vpn
    AppRoute.DPI -> R.string.route_dpi
}
