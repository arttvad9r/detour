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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.data.AppInfo
import dev.triplet.app.data.AppInventory
import dev.triplet.app.data.AppRouteOrdering
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val AppsContentMaxWidth = 840.dp
private val AppRouteInlineMinWidth = 292.dp

@Composable
fun AppsScreen(viewModel: AppsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val c = detourColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchHint = stringResource(R.string.search_hint)

    var searchFocused by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.refreshInventory()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshInventory()
    }

    val searchBorder by animateColorAsState(
        if (searchFocused) c.accent else c.border,
        tween(Motion.COLOR_MS), label = "searchBorder",
    )
    val searchIcon by animateColorAsState(
        if (searchFocused) c.accent else c.textMuted,
        tween(Motion.COLOR_MS), label = "searchIcon",
    )
    val showSystem = state.showSystemApps
    val loadedApps = state.loadedApps
    val allApps = loadedApps.orEmpty()
    val routeCounts = remember(allApps, state.routes) {
        AppRoute.entries.associateWith { route ->
            allApps.count { app ->
                (state.routes[app.packageName] ?: AppRoute.DIRECT) == route
            }
        }
    }
    val directLabel = stringResource(routeLabel(AppRoute.DIRECT))
    val vpnLabel = stringResource(routeLabel(AppRoute.VPN))
    val dpiLabel = stringResource(routeLabel(AppRoute.DPI))
    val routeSummary = "$directLabel ${routeCounts[AppRoute.DIRECT] ?: 0} · " +
        "$vpnLabel ${routeCounts[AppRoute.VPN] ?: 0} · " +
        "$dpiLabel ${routeCounts[AppRoute.DPI] ?: 0}"

    val screenOrder = remember(allApps) {
        AppRouteOrdering.snapshot(allApps, state.routes)
    }
    val apps = remember(allApps, screenOrder, state.query, showSystem) {
        val ordered = AppRouteOrdering.apply(allApps, screenOrder)
        ordered
            .filter { showSystem || !it.isSystem }
            .filter {
                state.query.isBlank() ||
                    it.label.contains(state.query, ignoreCase = true) ||
                    it.packageName.contains(state.query, ignoreCase = true)
            }
    }
    val appKeys = remember(apps) { apps.map { it.packageName } }
    val listState = rememberLazyListState()
    var listMotionActive by remember { mutableStateOf(false) }
    LaunchedEffect(state.query, showSystem, appKeys) {
        listMotionActive = true
        delay(Motion.LIST_REFRESH_BOOST_MS)
        listMotionActive = false
    }

    fun setShowSystemFromRow(value: Boolean) {
        haptics.performHapticFeedback(
            if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
        viewModel.setShowSystem(value)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        DetourBrandedHeader(stringResource(R.string.routes_title), onBack)
        Spacer(Modifier.height(Spacing.space4))

        DetourCard(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = AppsContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.space16),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space12, vertical = Spacing.space8)
                    .heightIn(min = 52.dp)
                    .clip(AppShapes.extraSmall)
                    .background(c.surfaceSoft)
                    .border(1.dp, searchBorder, AppShapes.extraSmall)
                    .padding(horizontal = Spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_search), null,
                    tint = searchIcon,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.space8))
                Box(Modifier.weight(1f)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.query.isEmpty(),
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
                        value = state.query,
                        onValueChange = viewModel::setQuery,
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

            GroupDivider(startInset = 16)
            Row(
                Modifier
                    .fillMaxWidth()
                    .detourToggleable(
                        value = showSystem,
                        onValueChange = ::setShowSystemFromRow,
                        pressedColor = c.surfaceSelected.copy(alpha = 0.32f),
                        pressScale = Motion.PRESS_ROW,
                    )
                    .heightIn(min = 60.dp)
                    .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space4, bottom = Spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetourIconTile(R.drawable.ic_routes)
                Column(
                    modifier = Modifier
                        .padding(start = Spacing.space12)
                        .weight(1f),
                ) {
                    Text(
                        stringResource(R.string.show_system),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = c.textPrimary,
                    )
                    if (loadedApps != null) {
                        Text(
                            routeSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Spacing.space2),
                        )
                    }
                }
                DetourSwitch(
                    checked = showSystem,
                    onCheckedChange = null,
                    compact = true,
                )
            }
        }

        Spacer(Modifier.height(Spacing.space8))
        if (state.inventoryStatus == AppsInventoryStatus.ERROR && loadedApps != null) {
            InventoryErrorBanner(
                onRetry = viewModel::refreshInventory,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = AppsContentMaxWidth)
                    .fillMaxWidth()
                    .padding(
                        start = Spacing.space16,
                        end = Spacing.space16,
                        bottom = Spacing.space8,
                    ),
            )
        }

        DetourCard(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = AppsContentMaxWidth)
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Spacing.space16),
        ) {
            when {
                state.inventoryStatus == AppsInventoryStatus.LOADING && loadedApps == null -> {
                    AppsStateMessage(
                        text = stringResource(R.string.routes_loading),
                        loading = true,
                    )
                }
                state.inventoryStatus == AppsInventoryStatus.ERROR && loadedApps == null -> {
                    AppsStateMessage(
                        text = stringResource(R.string.routes_load_error),
                        onRetry = viewModel::refreshInventory,
                    )
                }
                loadedApps?.isEmpty() == true -> {
                    AppsStateMessage(stringResource(R.string.routes_empty))
                }
                loadedApps != null && apps.isEmpty() -> {
                    AppsStateMessage(stringResource(R.string.routes_no_results))
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .detourHighRefresh(listState.isScrollInProgress || listMotionActive),
                    ) {
                        itemsIndexed(apps, key = { _, app -> app.packageName }) { i, app ->
                            val current = state.routes[app.packageName] ?: AppRoute.DIRECT
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
                                    viewModel.setAppRoute(app.packageName, route)
                                }
                                if (i < apps.lastIndex) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 52.dp)
                                            .height(1.dp)
                                            .background(c.divider),
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(Spacing.space16)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsStateMessage(
    text: String,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    val c = detourColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = c.accent,
            )
            Spacer(Modifier.height(Spacing.space12))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(Spacing.space8))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun InventoryErrorBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    Row(
        modifier
            .clip(AppShapes.small)
            .background(c.errorSoft)
            .border(1.dp, c.error.copy(alpha = 0.28f), AppShapes.small)
            .padding(start = Spacing.space16, end = Spacing.space4, top = Spacing.space4, bottom = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = c.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.routes_refresh_error),
            style = MaterialTheme.typography.bodySmall,
            color = c.textPrimary,
            modifier = Modifier
                .padding(start = Spacing.space8)
                .weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry), color = c.error)
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
    val density = LocalDensity.current
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

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space12, vertical = Spacing.space8),
    ) {
        val inline = maxWidth >= AppRouteInlineMinWidth && density.fontScale <= 1.30f
        if (inline) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIdentity(
                    app = app,
                    bitmap = bmp,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.space8))
                AppRouteSelector(
                    current = current,
                    onSelect = onSelect,
                    modifier = Modifier.width(176.dp),
                )
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                AppIdentity(app = app, bitmap = bmp, compact = false)
                Spacer(Modifier.height(Spacing.space8))
                AppRouteSelector(current = current, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun AppIdentity(
    app: AppInfo,
    bitmap: Bitmap?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val tileSize = if (compact) 34.dp else 38.dp
    val imageSize = if (compact) 28.dp else 30.dp
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(tileSize)
                .background(c.surfaceSoft, AppShapes.extraSmall)
                .border(1.dp, c.border.copy(alpha = 0.72f), AppShapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(imageSize)
                        .clip(AppShapes.extraSmall),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_routes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = c.textMuted,
                )
            }
        }
        Column(
            Modifier
                .padding(start = Spacing.space8)
                .weight(1f),
        ) {
            Text(
                app.label,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.space2),
                )
            }
        }
    }
}

@Composable
private fun AppRouteSelector(
    current: AppRoute,
    onSelect: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedControl(
        options = AppRoute.entries.map { stringResource(routeLabel(it)) },
        selected = AppRoute.entries.indexOf(current),
        onSelect = { idx -> onSelect(AppRoute.entries[idx]) },
        modifier = modifier,
    )
}

fun routeLabel(r: AppRoute): Int = when (r) {
    AppRoute.DIRECT -> R.string.route_direct
    AppRoute.VPN -> R.string.route_vpn
    AppRoute.DPI -> R.string.route_dpi
}
