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
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(stringResource(R.string.routes_title), onBack)
        Spacer(Modifier.height(Spacing.space4))

        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = AppsContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.space16)
                .heightIn(min = 56.dp)
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

        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = AppsContentMaxWidth)
                .fillMaxWidth()
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
            .padding(start = Spacing.space16, end = Spacing.space4, top = Spacing.space4, bottom = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.routes_refresh_error),
            style = MaterialTheme.typography.bodySmall,
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
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
        )
    }
}

fun routeLabel(r: AppRoute): Int = when (r) {
    AppRoute.DIRECT -> R.string.route_direct
    AppRoute.VPN -> R.string.route_vpn
    AppRoute.DPI -> R.string.route_dpi
}
