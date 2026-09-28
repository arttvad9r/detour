package dev.detour.app.ui

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.detour.app.DetourApp
import dev.detour.app.R
import dev.detour.app.core.ParseResult
import dev.detour.app.core.VlessKeyParser
import dev.detour.app.vpn.VpnController
import dev.detour.app.vpn.VpnState
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

private const val MAX_SUBSCRIPTION_NODE_ROWS = 256

/** Search only earns its space once the list no longer fits on one screen. */
private const val SUBSCRIPTION_SEARCH_MIN_NODES = 8

/** Expiry is highlighted during the last few days so renewal is not a surprise. */
private const val SUBSCRIPTION_EXPIRY_WARNING_DAYS = 3L

internal enum class SubscriptionSortOrder { DEFAULT, LATENCY }

/**
 * Filters by a case-insensitive name match, then optionally orders by measured
 * latency. Failed or untested nodes keep their provider order at the end.
 */
internal fun arrangeSubscriptionNodes(
    nodes: List<SubscriptionCatalogNode>,
    query: String,
    order: SubscriptionSortOrder,
    latencyByName: Map<String, Int>,
): List<SubscriptionCatalogNode> {
    val needle = query.trim()
    val filtered = if (needle.isEmpty()) nodes else nodes.filter { it.name.contains(needle, ignoreCase = true) }
    return when (order) {
        SubscriptionSortOrder.DEFAULT -> filtered
        SubscriptionSortOrder.LATENCY -> filtered.sortedBy { latencyByName[it.name] ?: Int.MAX_VALUE }
    }
}

/** Whole days until expiry, rounded up; negative once the plan has expired. */
internal fun subscriptionDaysLeft(expireAtEpochSeconds: Long, nowMillis: Long): Long {
    val remainingMs = TimeUnit.SECONDS.toMillis(expireAtEpochSeconds) - nowMillis
    if (remainingMs <= 0L) return -1L
    return (remainingMs + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)
}

@Composable
internal fun SubscriptionRuntimeSection(modifier: Modifier = Modifier) {
    val runtimeViewModel = viewModel<SubscriptionRuntimeViewModel>()
    val state by runtimeViewModel.uiState.collectAsStateWithLifecycle()
    val vpnState by VpnController.state.collectAsStateWithLifecycle()
    val connected = vpnState == VpnState.Active
    val context = LocalContext.current
    val store = remember(context) { (context.applicationContext as DetourApp).routesStore }
    val settings by store.settings.collectAsStateWithLifecycle()
    val activeKey = settings?.vlessKeys?.active
    val activeUri = activeKey?.uri.orEmpty()
    val persistedSelectedNode = activeKey?.selectedNode
    val subscriptionUrl = remember(activeUri) {
        (VlessKeyParser.parse(activeUri) as? ParseResult.Ok)?.profile?.subscriptionUrl
    }
    val cacheDir = remember(context) { context.cacheDir.absolutePath }
    var showServers by rememberSaveable { mutableStateOf(false) }

    val persistSelectedNode: suspend (String) -> Unit = { selected ->
        val key = activeKey
        if (key != null) {
            val latest = store.snapshot().vlessKeys.items.firstOrNull {
                it.id == key.id && it.uri == key.uri
            }
            if (latest != null && latest.selectedNode != selected) {
                store.updateVlessKey(latest.copy(selectedNode = selected))
            }
        }
    }

    LaunchedEffect(subscriptionUrl, connected, cacheDir, persistedSelectedNode) {
        subscriptionUrl?.let {
            runtimeViewModel.bind(
                subscriptionUrl = it,
                connected = connected,
                cacheDir = cacheDir,
                persistedSelectedNode = persistedSelectedNode,
            )
        }
    }

    // Reconcile a live selector value that was chosen by the engine itself, for
    // example after a provider refresh removed the previously selected node.
    LaunchedEffect(activeKey, state.catalog, state.selectedNode, state.selectionStatus) {
        val key = activeKey ?: return@LaunchedEffect
        val selected = state.selectedNode?.trim()?.takeIf { it.isNotBlank() }
            ?: return@LaunchedEffect
        if (
            state.selectionStatus == SubscriptionSelectionStatus.IDLE &&
            state.catalog.any { it.name == selected } &&
            key.selectedNode != selected
        ) {
            store.updateVlessKey(key.copy(selectedNode = selected))
        }
    }

    // Adopt the provider's own title while the profile still carries the
    // auto-generated host name; a name the user typed is never overwritten.
    LaunchedEffect(activeKey?.id, state.info?.title) {
        val key = activeKey ?: return@LaunchedEffect
        val title = state.info?.title ?: return@LaunchedEffect
        val host = (VlessKeyParser.parse(key.uri) as? ParseResult.Ok)?.profile?.server
        if (shouldAdoptSubscriptionTitle(key.name, host, title)) {
            store.updateVlessKey(key.copy(name = title))
        }
    }

    LaunchedEffect(state.catalog, state.selectedNode, state.selectionStatus) {
        if (
            state.catalog.isNotEmpty() &&
            shouldAutoSelectSubscriptionNode(state.selectionStatus) &&
            state.selectedNode.isNullOrBlank()
        ) {
            runtimeViewModel.selectNode(state.catalog.first().name, persistSelectedNode)
        }
    }

    val runtimeNodes = remember(state.provider.nodes) {
        state.provider.nodes.associateBy { it.name }
    }
    val latencyByName = remember(runtimeNodes, state.latencyByName) {
        buildMap {
            runtimeNodes.forEach { (name, node) -> node.delayMs?.let { put(name, it) } }
            putAll(state.latencyByName)
        }
    }
    val latencyTestedNames = remember(runtimeNodes, state.latencyTestedNames) {
        buildSet {
            runtimeNodes.forEach { (name, node) -> if (node.delayMs != null) add(name) }
            addAll(state.latencyTestedNames)
        }
    }

    val catalogLoading = state.catalogStatus == SubscriptionCatalogStatus.LOADING ||
        state.status == SubscriptionRuntimeStatus.REFRESHING
    val selectedNode = state.selectedNode?.takeIf { name -> state.catalog.any { it.name == name } }
    val selectedDelay = selectedNode?.let { latencyByName[it] }
    val serverSubtitle = when {
        selectedNode != null && selectedDelay != null ->
            stringResource(R.string.subscription_server_with_delay, selectedNode, selectedDelay)
        selectedNode != null -> selectedNode
        catalogLoading -> stringResource(R.string.subscription_runtime_loading)
        state.catalogStatus == SubscriptionCatalogStatus.ERROR -> stringResource(R.string.subscription_catalog_error_short)
        else -> stringResource(R.string.subscription_select_server)
    }

    Column(modifier.padding(horizontal = Spacing.space16)) {
        DetourCard(Modifier.animateContentSize(Motion.SIZE_SPEC)) {
            state.info?.takeIf { it.hasTraffic || it.hasExpiry || it.usedBytes > 0L }?.let { info ->
                SubscriptionInfoBlock(info)
                GroupDivider(startInset = Spacing.space16.value.toInt())
            }
            DetourNavigationRow(
                title = stringResource(R.string.subscription_selected_server),
                subtitle = serverSubtitle,
                iconRes = R.drawable.ic_server,
                onClick = { showServers = true },
                trailing = if (catalogLoading && state.catalog.isEmpty()) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = Spacing.space4)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                            color = detourColors.accent,
                        )
                    }
                } else {
                    null
                },
            )
        }

        AnimatedVisibility(
            visible = state.selectionStatus == SubscriptionSelectionStatus.ERROR,
            enter = Motion.reveal,
            exit = Motion.conceal,
        ) {
            SubscriptionNotice(
                text = stringResource(R.string.subscription_selection_error),
                error = true,
                modifier = Modifier.padding(top = Spacing.space8),
            )
        }
    }

    if (showServers) {
        SubscriptionServerSheet(
            state = state,
            subscriptionKey = subscriptionUrl.orEmpty(),
            loading = catalogLoading,
            latencyByName = latencyByName,
            latencyTestedNames = latencyTestedNames,
            onDismiss = { showServers = false },
            onRefresh = { runtimeViewModel.refresh(connected) },
            onTestLatency = runtimeViewModel::testLatency,
            onSelect = { name -> runtimeViewModel.selectNode(name, persistSelectedNode) },
        )
    }
}

@Composable
private fun SubscriptionInfoBlock(info: SubscriptionInfo) {
    val c = detourColors
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    val daysLeft = if (info.hasExpiry) subscriptionDaysLeft(info.expireAtEpochSeconds, now) else null
    val expiryWarning = daysLeft != null && daysLeft <= SUBSCRIPTION_EXPIRY_WARNING_DAYS
    val expiryText = when {
        daysLeft == null -> null
        daysLeft < 0 -> stringResource(R.string.subscription_expired)
        else -> {
            val date = remember(info.expireAtEpochSeconds) {
                DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(Date(TimeUnit.SECONDS.toMillis(info.expireAtEpochSeconds)))
            }
            stringResource(R.string.subscription_expires_on, date)
        }
    }

    // The provider title already names the profile row, so this block only
    // carries the numbers: traffic on the left, expiry on the right.
    val trafficText = when {
        info.hasTraffic -> stringResource(
            R.string.subscription_traffic_left,
            Formatter.formatShortFileSize(context, info.remainingBytes),
            Formatter.formatShortFileSize(context, info.totalBytes),
        )
        info.usedBytes > 0L -> stringResource(
            R.string.subscription_traffic_unlimited,
            Formatter.formatShortFileSize(context, info.usedBytes),
        )
        else -> stringResource(R.string.subscription_plan)
    }

    Column(Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space12)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = trafficText,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (expiryText != null) {
                Text(
                    text = expiryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expiryWarning) c.error else c.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Spacing.space8),
                )
            }
        }

        if (info.hasTraffic) {
            val progress by animateFloatAsState(
                targetValue = info.usedFraction,
                animationSpec = tween(Motion.NAV_ENTER_MS, easing = Motion.ENTER_EASING),
                label = "subscriptionUsage",
            )
            val nearlyExhausted = info.usedFraction >= 0.9f
            Spacer(Modifier.height(Spacing.space12))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(PillShape)
                    .background(c.surfaceSoft),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(PillShape)
                        .background(if (nearlyExhausted) c.error else c.accent),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionServerSheet(
    state: SubscriptionRuntimeUiState,
    subscriptionKey: String,
    loading: Boolean,
    latencyByName: Map<String, Int>,
    latencyTestedNames: Set<String>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onTestLatency: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val c = detourColors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp

    var query by rememberSaveable(subscriptionKey) { mutableStateOf("") }
    var sortOrderName by rememberSaveable(subscriptionKey) { mutableStateOf(SubscriptionSortOrder.DEFAULT.name) }
    val sortOrder = runCatching { SubscriptionSortOrder.valueOf(sortOrderName) }
        .getOrDefault(SubscriptionSortOrder.DEFAULT)
    val nodes = remember(state.catalog, query, sortOrder, latencyByName) {
        arrangeSubscriptionNodes(
            nodes = state.catalog.take(MAX_SUBSCRIPTION_NODE_ROWS),
            query = query,
            order = sortOrder,
            latencyByName = latencyByName,
        )
    }
    val selecting = state.selectionStatus == SubscriptionSelectionStatus.SAVING

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.background,
        contentColor = c.textPrimary,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = Spacing.space16),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.subscription_runtime_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = c.textPrimary,
                    )
                    if (state.catalog.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(R.plurals.subscription_server_count, state.catalog.size, state.catalog.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onTestLatency()
                    },
                    enabled = state.catalog.isNotEmpty() && !state.latencyTesting && !loading,
                ) {
                    if (state.latencyTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = c.accent,
                        )
                        Spacer(Modifier.width(Spacing.space8))
                    }
                    Text(
                        text = stringResource(
                            if (state.latencyTesting) {
                                R.string.subscription_latency_testing
                            } else {
                                R.string.subscription_latency_test
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                DetourIconButton(
                    onClick = { if (!loading && !state.latencyTesting) onRefresh() },
                    size = 40,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = c.accent,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.subscription_runtime_refresh),
                            tint = c.textPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.space12))

            if (state.catalog.size >= SUBSCRIPTION_SEARCH_MIN_NODES) {
                SubscriptionSearchField(query = query, onQueryChange = { query = it })
                Spacer(Modifier.height(Spacing.space8))
            }
            if (state.catalog.isNotEmpty()) {
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.subscription_sort_default),
                        stringResource(R.string.subscription_sort_latency),
                    ),
                    selected = sortOrder.ordinal,
                    onSelect = { index ->
                        sortOrderName = SubscriptionSortOrder.entries[index].name
                        if (
                            index == SubscriptionSortOrder.LATENCY.ordinal &&
                            latencyTestedNames.isEmpty() &&
                            !state.latencyTesting &&
                            !loading
                        ) {
                            onTestLatency()
                        }
                    },
                )
                Spacer(Modifier.height(Spacing.space12))
            }

            when {
                state.catalog.isNotEmpty() && nodes.isEmpty() -> SubscriptionNotice(
                    text = stringResource(R.string.subscription_search_empty, query.trim()),
                )
                state.catalog.isNotEmpty() -> DetourCard(Modifier.weight(1f, fill = false)) {
                    LazyColumn(Modifier.selectableGroup()) {
                        itemsIndexed(nodes, key = { _, node -> node.name }) { index, node ->
                            SubscriptionNodeRow(
                                name = node.name,
                                selected = node.name == state.selectedNode,
                                enabled = !selecting,
                                delayMs = latencyByName[node.name],
                                latencyTested = node.name in latencyTestedNames,
                                latencyError = state.latencyErrorByName[node.name],
                                onSelect = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(node.name)
                                    scope.launch {
                                        runCatching { sheetState.hide() }
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                            if (index < nodes.lastIndex) GroupDivider(startInset = ChoiceRowDividerInset)
                        }
                    }
                }
                loading -> SubscriptionNotice(
                    text = stringResource(R.string.subscription_runtime_loading),
                    loading = true,
                )
                state.catalogStatus == SubscriptionCatalogStatus.ERROR -> SubscriptionNotice(
                    text = stringResource(R.string.subscription_catalog_error),
                    error = true,
                )
            }

            val hiddenCount = (state.catalog.size - MAX_SUBSCRIPTION_NODE_ROWS).coerceAtLeast(0)
            if (hiddenCount > 0) {
                Text(
                    text = stringResource(R.string.subscription_nodes_more, hiddenCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space8),
                )
            }
            Spacer(Modifier.navigationBarsPadding().height(Spacing.space16))
        }
    }
}

@Composable
private fun SubscriptionSearchField(query: String, onQueryChange: (String) -> Unit) {
    val c = detourColors
    val hint = stringResource(R.string.subscription_search_hint)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(AppShapes.extraSmall)
            .background(c.surface)
            .border(1.dp, c.border, AppShapes.extraSmall)
            .padding(horizontal = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = c.textMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.space8))
        Box(Modifier.weight(1f)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = query.isEmpty(),
                enter = fadeIn(tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING)),
                exit = fadeOut(tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING)),
            ) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = hint },
            )
        }
    }
}

@Composable
private fun SubscriptionNodeRow(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    delayMs: Int?,
    latencyTested: Boolean,
    latencyError: SubscriptionLatencyError?,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val theme = LocalDetourTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .detourSelectable(
                selected = selected,
                onClick = { if (enabled && !selected) onSelect() },
                idleColor = if (selected) c.surfaceSoft else Color.Transparent,
                pressedColor = if (selected) c.surfaceSoft else c.surfaceSelected,
                pressScale = Motion.PRESS_RADIO,
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected = selected)
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        )
        if (delayMs != null || latencyTested) {
            val failed = latencyError != null || delayMs == null
            Text(
                text = delayMs?.let { stringResource(R.string.subscription_node_delay, it) }
                    ?: stringResource(R.string.subscription_latency_timeout),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = if (failed) latencyBadColor(theme) else latencyColorFor(theme, delayMs),
                textAlign = TextAlign.End,
                modifier = Modifier
                    .padding(start = Spacing.space8)
                    .widthIn(min = 56.dp),
            )
        }
    }
}

@Composable
private fun SubscriptionNotice(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    loading: Boolean = false,
) {
    val c = detourColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (error) c.errorSoft else c.surfaceSoft, AppShapes.small)
            .border(
                1.dp,
                if (error) c.error.copy(alpha = 0.32f) else c.border,
                AppShapes.small,
            )
            .padding(Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = c.accent,
            )
        } else {
            Icon(
                painter = painterResource(if (error) R.drawable.ic_warning else R.drawable.ic_server),
                contentDescription = null,
                tint = if (error) c.error else c.accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = c.textPrimary,
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        )
    }
}

internal fun shouldAdoptSubscriptionTitle(currentName: String, host: String?, title: String): Boolean {
    val name = currentName.trim()
    return title.isNotBlank() && name != title && (name.isEmpty() || name.equals(host, ignoreCase = true))
}

internal fun shouldAutoSelectSubscriptionNode(status: SubscriptionSelectionStatus): Boolean =
    status == SubscriptionSelectionStatus.IDLE
