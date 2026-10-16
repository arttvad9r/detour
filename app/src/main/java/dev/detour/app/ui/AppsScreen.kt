package dev.detour.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.detour.app.R
import dev.detour.app.core.AppRoute
import dev.detour.app.data.AppInfo
import dev.detour.app.data.AppInventory
import dev.detour.app.data.AppRouteOrdering
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val AppsContentMaxWidth = 840.dp
private val AppRouteInlineMinWidth = 260.dp
private val AppRouteSelectorWidth = 166.dp
private val DenseAppRowMinHeight = 48.dp
private val DenseAppTileSize = 32.dp
private val DenseAppImageSize = 26.dp
private val DenseAppContentGap = Spacing.space8
private const val DenseAppRowDividerInset = 52

@Composable
fun AppsScreen(
    viewModel: AppsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    setupRoute: AppRoute? = null,
    canContinue: Boolean = false,
    onContinue: (() -> Unit)? = null,
    onOpenApp: (String) -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val c = detourColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val writeState by viewModel.routeWriteState.collectAsStateWithLifecycle()
    var filterIndex by rememberSaveable { mutableStateOf(0) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedPackages by rememberSaveable(state.query, state.showSystemApps, filterIndex) {
        mutableStateOf(emptyList<String>())
    }
    LaunchedEffect(writeState.completedBatches) {
        if (writeState.completedBatches > 0) {
            selectedPackages = emptyList()
            selecting = false
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchHint = stringResource(R.string.search_hint)
    var searchFocused by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.refreshInventory() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshInventory() }

    val searchBorder by animateColorAsState(
        if (searchFocused) c.accent else c.border,
        tween(Motion.COLOR_MS),
        label = "searchBorder",
    )
    val searchIcon by animateColorAsState(
        if (searchFocused) c.accent else c.textMuted,
        tween(Motion.COLOR_MS),
        label = "searchIcon",
    )

    val showSystem = state.showSystemApps
    val loadedApps = state.loadedApps
    val allApps = loadedApps.orEmpty()
    val routeCounts = remember(allApps, state.routes) {
        AppRoute.entries.associateWith { route ->
            allApps.count { app -> (state.routes[app.packageName] ?: AppRoute.DIRECT) == route }
        }
    }
    val directLabel = stringResource(routeCompactLabel(AppRoute.DIRECT))
    val vpnLabel = stringResource(routeLabel(AppRoute.VPN))
    val dpiLabel = stringResource(routeLabel(AppRoute.DPI))
    val routeSummary = "$directLabel ${routeCounts[AppRoute.DIRECT] ?: 0} · " +
        "$vpnLabel ${routeCounts[AppRoute.VPN] ?: 0} · " +
        "$dpiLabel ${routeCounts[AppRoute.DPI] ?: 0}"

    val screenOrder = remember(allApps) { AppRouteOrdering.snapshot(allApps, state.routes) }
    val apps = remember(allApps, screenOrder, state.query, showSystem, state.routes, filterIndex) {
        filterApps(
            AppRouteOrdering.apply(allApps, screenOrder), state.routes, state.query,
            showSystem, AppsRouteFilter.entries[filterIndex],
        )
    }
    LaunchedEffect(allApps) {
        val installed = allApps.mapTo(hashSetOf()) { it.packageName }
        selectedPackages = selectedPackages.filter { it in installed }
    }
    val appKeys = remember(apps) { apps.map { it.packageName } }
    val listState = rememberLazyListState()
    var listMotionActive by remember { mutableStateOf(false) }
    LaunchedEffect(state.query, showSystem, filterIndex, selecting) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(state.query, showSystem, appKeys) {
        listMotionActive = true
        delay(Motion.LIST_REFRESH_BOOST_MS)
        listMotionActive = false
    }

    fun setShowSystemFromRow(value: Boolean) {
        haptics.performHapticFeedback(
            if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
        selectedPackages = emptyList()
        viewModel.setShowSystem(value)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        DetourBrandedHeader(stringResource(R.string.routes_title), {
            if (selecting) {
                selecting = false
                selectedPackages = emptyList()
            } else onBack()
        })
        Spacer(Modifier.height(Spacing.space4))

        if (!selecting) {
            DetourCard(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = AppsContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space16),
            ) {
                if (setupRoute != null) {
                    Text(
                        stringResource(if (setupRoute == AppRoute.DPI) R.string.setup_apps_dpi else R.string.setup_apps_vpn),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        modifier = Modifier.padding(Spacing.space12),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.space12, vertical = Spacing.space4)
                        .heightIn(min = DenseAppRowMinHeight)
                        .clip(AppShapes.extraSmall)
                        .background(c.surface)
                        .border(1.dp, searchBorder, AppShapes.extraSmall)
                        .padding(horizontal = Spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
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
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textMuted,
                            )
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = {
                                selectedPackages = emptyList()
                                viewModel.setQuery(it)
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { searchFocused = it.isFocused }
                                .semantics { contentDescription = searchHint },
                        )
                    }
                }

                GroupDivider(startInset = Spacing.space16.value.toInt())
                DetourNavigationRow(
                    title = stringResource(R.string.show_system),
                    subtitle = routeSummary.takeIf { loadedApps != null },
                    iconRes = R.drawable.ic_routes,
                    modifier = Modifier.detourToggleable(
                        value = showSystem,
                        onValueChange = ::setShowSystemFromRow,
                        pressedColor = c.surfaceSelected.copy(alpha = 0.32f),
                        pressScale = Motion.PRESS_ROW,
                    ),
                    trailing = {
                        DetourSwitch(
                            checked = showSystem,
                            onCheckedChange = null,
                            compact = true,
                        )
                    },
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = AppsContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        ) {
            RouteFilters(filterIndex, onSelect = { filterIndex = it })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selecting) stringResource(R.string.routes_selected_count, selectedPackages.size)
                        else stringResource(R.string.routes_visible_count, apps.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = loadedApps != null && !writeState.saving && (selecting || apps.isNotEmpty()),
                    onClick = {
                        keyboard?.hide()
                        selecting = !selecting
                        selectedPackages = emptyList()
                    },
                ) {
                    Text(stringResource(if (selecting) R.string.routes_cancel_selection else R.string.routes_select))
                }
            }
            if (selecting) {
                TextButton(
                    enabled = apps.isNotEmpty() && !writeState.saving,
                    onClick = { selectedPackages = apps.map { it.packageName } },
                ) { Text(stringResource(R.string.routes_select_visible)) }
            }
            if (writeState.error) {
                Text(
                    stringResource(R.string.routes_save_error),
                    color = c.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = Spacing.space4),
                )
            }
        }
        if (state.inventoryStatus == AppsInventoryStatus.ERROR && loadedApps != null) {
            InventoryErrorBanner(
                onRetry = viewModel::refreshInventory,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = AppsContentMaxWidth)
                    .fillMaxWidth()
                    .padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space8),
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
                    AppsStateMessage(stringResource(R.string.routes_loading), loading = true)
                }
                state.inventoryStatus == AppsInventoryStatus.ERROR && loadedApps == null -> {
                    AppsStateMessage(
                        text = stringResource(R.string.routes_load_error),
                        onRetry = viewModel::refreshInventory,
                    )
                }
                loadedApps?.isEmpty() == true -> AppsStateMessage(stringResource(R.string.routes_empty))
                loadedApps != null && apps.isEmpty() -> AppsStateMessage(stringResource(R.string.routes_no_results))
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .detourHighRefresh(listState.isScrollInProgress || listMotionActive),
                    ) {
                        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                            val current = state.routes[app.packageName] ?: AppRoute.DIRECT
                            val shape = when {
                                apps.size == 1 -> AppShapes.small
                                index == 0 -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                                index == apps.lastIndex -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
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
                                AppRow(
                                    app = app,
                                    current = current,
                                    selecting = selecting,
                                    selected = app.packageName in selectedPackages,
                                    selectionEnabled = !writeState.saving,
                                    onToggle = {
                                        selectedPackages = if (app.packageName in selectedPackages) {
                                            selectedPackages - app.packageName
                                        } else selectedPackages + app.packageName
                                    },
                                    onSelect = { route -> viewModel.setAppRoute(app.packageName, route) },
                                    onOpen = { onOpenApp(app.packageName) },
                                )
                                if (index < apps.lastIndex) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = DenseAppRowDividerInset.dp)
                                            .height(1.dp)
                                            .background(c.divider),
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(Spacing.space8)) }
                    }
                }
            }
        }
        if (!selecting && onContinue != null) {
            DetourContentColumn {
                DetourButton(
                    text = stringResource(R.string.setup_continue),
                    enabled = canContinue && !writeState.saving,
                    onClick = onContinue,
                    modifier = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space8)
                        .testTag("setup-routes-continue"),
                )
            }
        }
        if (selecting) {
            Column(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = AppsContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
            ) {
                Text(
                    stringResource(if (writeState.saving) R.string.routes_saving else R.string.routes_assign_route),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                )
                val largeFont = LocalDensity.current.fontScale > 1.5f
                Column(Modifier.fillMaxWidth().testTag("apps-bulk-route-actions")) {
                    AppRoute.entries.chunked(if (largeFont) 1 else 3).forEach { routes ->
                        Row(Modifier.fillMaxWidth()) {
                            routes.forEach { route ->
                                TextButton(
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    enabled = selectedPackages.isNotEmpty() && !writeState.saving,
                                    onClick = { viewModel.setAppRoutes(selectedPackages.toSet(), route) },
                                ) { Text(stringResource(routeCompactLabel(route))) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteFilters(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(stringResource(R.string.routes_filter_all)) +
        AppRoute.entries.map { stringResource(routeCompactLabel(it)) }
    val largeFont = LocalDensity.current.fontScale > 1.5f
    Column(Modifier.testTag("apps-route-filter"), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        labels.chunked(if (largeFont) 2 else 4).forEachIndexed { row, options ->
            val offset = row * 2
            SegmentedControl(
                options = options,
                selected = if (selected in offset until offset + options.size) selected - offset else -1,
                onSelect = { onSelect(offset + it) },
            )
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
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
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
            modifier = Modifier.padding(start = Spacing.space8).weight(1f),
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry), color = c.error) }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    current: AppRoute,
    selecting: Boolean,
    selected: Boolean,
    selectionEnabled: Boolean,
    onToggle: () -> Unit,
    onSelect: (AppRoute) -> Unit,
    onOpen: () -> Unit,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val bitmap by produceState<Bitmap?>(
        initialValue = AppInventory.peekIcon(app.packageName),
        key1 = app.packageName,
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { AppInventory.loadIcon(ctx, app.packageName) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val inline = maxWidth >= AppRouteInlineMinWidth && density.fontScale <= 1.50f
        if (selecting) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("app-selection-${app.packageName}")
                    .background(if (selected) detourColors.surfaceSelected else Color.Transparent)
                    .toggleable(
                        value = selected, enabled = selectionEnabled,
                        role = Role.Checkbox, onValueChange = { onToggle() },
                    )
                    .heightIn(min = DenseAppRowMinHeight)
                    .padding(horizontal = Spacing.space12, vertical = Spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selected, onCheckedChange = null, enabled = selectionEnabled)
                Spacer(Modifier.width(Spacing.space8))
                AppIdentity(app, bitmap, compact = false, modifier = Modifier.weight(1f))
                Text(
                    stringResource(routeCompactLabel(current)),
                    style = MaterialTheme.typography.labelSmall,
                    color = detourColors.textSecondary,
                    modifier = Modifier.padding(start = Spacing.space8),
                )
            }
        } else if (inline) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DenseAppRowMinHeight)
                    .padding(horizontal = Spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIdentity(
                    app = app,
                    bitmap = bitmap,
                    compact = true,
                    showChevron = true,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("app-detail-${app.packageName}")
                        .detourClickable(onClick = onOpen, role = Role.Button),
                )
                Spacer(Modifier.width(DenseAppContentGap))
                CompactRouteSelector(
                    current = current,
                    onSelect = onSelect,
                    modifier = Modifier.width(AppRouteSelectorWidth),
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space12, vertical = Spacing.space8),
            ) {
                AppIdentity(app = app, bitmap = bitmap, compact = false, showChevron = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("app-detail-${app.packageName}")
                        .detourClickable(onClick = onOpen, role = Role.Button))
                Spacer(Modifier.height(Spacing.space8))
                SegmentedControl(
                    options = AppRoute.entries.map { stringResource(routeLabel(it)) },
                    selected = AppRoute.entries.indexOf(current),
                    onSelect = { index -> onSelect(AppRoute.entries[index]) },
                )
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
    showChevron: Boolean = false,
) {
    val c = detourColors
    val tileSize = if (compact) DenseAppTileSize else 38.dp
    val imageSize = if (compact) DenseAppImageSize else 30.dp
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(tileSize)
                .background(c.surfaceSoft, AppShapes.extraSmall)
                .border(1.dp, c.border.copy(alpha = 0.72f), AppShapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(imageSize).clip(AppShapes.extraSmall),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_routes),
                    contentDescription = null,
                    modifier = Modifier.size(NavigationRowLeadingIconSize),
                    tint = c.textMuted,
                )
            }
        }
        Column(Modifier.padding(start = DenseAppContentGap).weight(1f)) {
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
        if (showChevron) Chevron()
    }
}

@Composable
private fun CompactRouteSelector(
    current: AppRoute,
    onSelect: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .height(DenseAppRowMinHeight)
            .selectableGroup(),
    ) {
        AppRoute.entries.forEach { route ->
            val selected = route == current
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(route)
                            }
                        },
                        role = Role.RadioButton,
                        interactionSource = interactionSource,
                        indication = null,
                    )
                    .padding(horizontal = 1.dp, vertical = 6.dp)
                    .background(
                        if (selected) c.surfaceSelected else c.surfaceSoft,
                        AppShapes.extraSmall,
                    )
                    .border(
                        1.dp,
                        if (selected) c.accentBorder else c.border.copy(alpha = 0.72f),
                        AppShapes.extraSmall,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(routeCompactLabel(route)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) c.accent else c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun routeCompactLabel(route: AppRoute): Int = when (route) {
    AppRoute.DIRECT -> R.string.route_direct_compact
    AppRoute.VPN -> R.string.route_vpn
    AppRoute.DPI -> R.string.route_dpi
}

fun routeLabel(route: AppRoute): Int = when (route) {
    AppRoute.DIRECT -> R.string.route_direct
    AppRoute.VPN -> R.string.route_vpn
    AppRoute.DPI -> R.string.route_dpi
}
