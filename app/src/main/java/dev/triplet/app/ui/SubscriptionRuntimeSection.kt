package dev.triplet.app.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.triplet.app.R
import dev.triplet.app.TripletApp
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val MAX_SUBSCRIPTION_NODE_ROWS = 256
private enum class SubscriptionSortOrder { DEFAULT, LATENCY, NAME }

@Composable
internal fun SubscriptionRuntimeSection(modifier: Modifier = Modifier) {
    val runtimeViewModel = viewModel<SubscriptionRuntimeViewModel>()
    val state by runtimeViewModel.uiState.collectAsStateWithLifecycle()
    val vpnState by VpnController.state.collectAsStateWithLifecycle()
    val connected = vpnState == VpnState.Active
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { (context.applicationContext as TripletApp).routesStore }
    val settings by store.settings.collectAsStateWithLifecycle()
    val activeKey = settings?.vlessKeys?.active
    val activeUri = activeKey?.uri.orEmpty()
    val persistedSelectedNode = activeKey?.selectedNode
    val favoriteNodes = activeKey?.favoriteNodes.orEmpty()
    val subscriptionUpdateIntervalHours = activeKey?.subscriptionUpdateIntervalHours
    val subscriptionUpdatedAt = activeKey?.subscriptionUpdatedAt
    val subscriptionUrl = remember(activeUri) {
        (VlessKeyParser.parse(activeUri) as? ParseResult.Ok)?.profile?.subscriptionUrl
    }
    val cacheDir = remember(context) { context.cacheDir.absolutePath }
    val c = detourColors

    var query by rememberSaveable(subscriptionUrl) { mutableStateOf("") }
    var favoritesOnly by rememberSaveable(subscriptionUrl) { mutableStateOf(false) }
    var sortOrderName by rememberSaveable(subscriptionUrl) { mutableStateOf(SubscriptionSortOrder.DEFAULT.name) }
    val sortOrder = runCatching { SubscriptionSortOrder.valueOf(sortOrderName) }
        .getOrDefault(SubscriptionSortOrder.DEFAULT)

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

    val persistRefreshedAt: suspend (Long) -> Unit = { refreshedAt ->
        val key = activeKey
        if (key != null) {
            val latest = store.snapshot().vlessKeys.items.firstOrNull {
                it.id == key.id && it.uri == key.uri
            }
            if (latest != null && latest.subscriptionUpdatedAt != refreshedAt) {
                store.updateVlessKey(latest.copy(subscriptionUpdatedAt = refreshedAt))
            }
        }
    }

    val setUpdateInterval: (Int?) -> Unit = { intervalHours ->
        val keyId = activeKey?.id
        if (keyId != null) {
            scope.launch {
                val latest = store.snapshot().vlessKeys.items.firstOrNull { it.id == keyId }
                    ?: return@launch
                if (latest.subscriptionUpdateIntervalHours != intervalHours) {
                    store.updateVlessKey(
                        latest.copy(subscriptionUpdateIntervalHours = intervalHours),
                    )
                }
            }
        }
    }

    val setFavorite: (String, Boolean) -> Unit = { nodeName, favorite ->
        val keyId = activeKey?.id
        if (keyId != null) {
            scope.launch {
                val latest = store.snapshot().vlessKeys.items.firstOrNull { it.id == keyId }
                    ?: return@launch
                val nextFavorites = latest.favoriteNodes.toMutableSet().apply {
                    if (favorite) add(nodeName) else remove(nodeName)
                }.toSet()
                if (nextFavorites != latest.favoriteNodes) {
                    store.updateVlessKey(latest.copy(favoriteNodes = nextFavorites))
                }
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
    val visibleNodes = remember(
        state.catalog,
        query,
        favoritesOnly,
        favoriteNodes,
        sortOrder,
        latencyByName,
    ) {
        val filtered = state.catalog.filter { node ->
            (!favoritesOnly || node.name in favoriteNodes) &&
                (query.isBlank() || node.name.contains(query.trim(), ignoreCase = true))
        }
        when (sortOrder) {
            SubscriptionSortOrder.DEFAULT -> filtered
            SubscriptionSortOrder.LATENCY -> filtered.sortedWith(
                compareBy<SubscriptionCatalogNode> { latencyByName[it.name] ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase() },
            )
            SubscriptionSortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
        }
    }

    Column(modifier.padding(horizontal = Spacing.space16)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.subscription_runtime_title),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = runtimeViewModel::testLatency,
                enabled = subscriptionUrl != null &&
                    state.catalog.isNotEmpty() &&
                    !state.latencyTesting &&
                    state.status != SubscriptionRuntimeStatus.REFRESHING,
            ) {
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
            TextButton(
                onClick = { runtimeViewModel.refresh(connected, persistRefreshedAt) },
                enabled = subscriptionUrl != null &&
                    state.catalogStatus != SubscriptionCatalogStatus.LOADING &&
                    state.status != SubscriptionRuntimeStatus.REFRESHING &&
                    !state.latencyTesting,
            ) {
                Text(
                    text = stringResource(
                        if (
                            state.catalogStatus == SubscriptionCatalogStatus.LOADING ||
                            state.status == SubscriptionRuntimeStatus.REFRESHING
                        ) {
                            R.string.subscription_runtime_refreshing
                        } else {
                            R.string.subscription_runtime_refresh
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        state.metadata?.let { metadata ->
            SubscriptionMetadataCard(metadata)
            Spacer(Modifier.height(Spacing.space12))
        }

        SubscriptionAutoUpdateCard(
            intervalHours = subscriptionUpdateIntervalHours,
            providerRecommendedHours = state.metadata?.updateIntervalHours,
            updatedAt = subscriptionUpdatedAt,
            onIntervalChange = setUpdateInterval,
        )
        Spacer(Modifier.height(Spacing.space12))

        if (state.catalog.isNotEmpty()) {
            SubscriptionCatalogControls(
                query = query,
                onQueryChange = { query = it },
                favoritesOnly = favoritesOnly,
                onFavoritesOnlyChange = { favoritesOnly = it },
                sortOrder = sortOrder,
                onSortOrderChange = { sortOrderName = it.name },
            )
            Spacer(Modifier.height(Spacing.space12))
        }

        when {
            state.catalog.isNotEmpty() && visibleNodes.isNotEmpty() -> {
                SubscriptionServerList(
                    nodes = visibleNodes.take(MAX_SUBSCRIPTION_NODE_ROWS),
                    selectedNode = state.selectedNode,
                    selecting = state.selectionStatus == SubscriptionSelectionStatus.SAVING,
                    favoriteNodes = favoriteNodes,
                    latencyByName = latencyByName,
                    latencyTestedNames = latencyTestedNames,
                    latencyErrorByName = state.latencyErrorByName,
                    onSelect = { name -> runtimeViewModel.selectNode(name, persistSelectedNode) },
                    onFavoriteChange = setFavorite,
                )
                val hiddenCount = (visibleNodes.size - MAX_SUBSCRIPTION_NODE_ROWS).coerceAtLeast(0)
                if (hiddenCount > 0) {
                    Text(
                        text = stringResource(R.string.subscription_nodes_more, hiddenCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                        modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space8),
                    )
                }
            }
            state.catalog.isNotEmpty() -> {
                SubscriptionNotice(text = stringResource(R.string.subscription_no_matches))
            }
            state.catalogStatus == SubscriptionCatalogStatus.LOADING -> {
                SubscriptionNotice(
                    text = stringResource(R.string.subscription_runtime_loading),
                    loading = true,
                )
            }
            state.catalogStatus == SubscriptionCatalogStatus.ERROR -> {
                SubscriptionNotice(
                    text = stringResource(R.string.subscription_catalog_error),
                    error = true,
                )
            }
        }

        if (state.selectionStatus == SubscriptionSelectionStatus.ERROR) {
            Spacer(Modifier.height(Spacing.space8))
            SubscriptionNotice(
                text = stringResource(R.string.subscription_selection_error),
                error = true,
            )
        }
    }
}

@Composable
private fun SubscriptionMetadataCard(metadata: SubscriptionMetadata) {
    val context = LocalContext.current
    val c = detourColors
    val total = metadata.totalBytes
    val remaining = metadata.remainingBytes
    val expiry = metadata.expireAtUnix?.let { seconds ->
        runCatching {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(
                Date(seconds.coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L),
            )
        }.getOrNull()
    }

    DetourCard {
        Column(Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space12)) {
            metadata.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (total != null) {
                val usedText = Formatter.formatShortFileSize(context, metadata.usedBytes)
                val totalText = Formatter.formatShortFileSize(context, total)
                Text(
                    text = stringResource(R.string.subscription_metadata_usage, usedText, totalText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                    modifier = Modifier.padding(top = Spacing.space4),
                )
                remaining?.let {
                    Text(
                        text = stringResource(
                            R.string.subscription_metadata_remaining,
                            Formatter.formatShortFileSize(context, it),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = Spacing.space2),
                    )
                }
            }
            expiry?.let {
                Text(
                    text = stringResource(R.string.subscription_metadata_expires, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Spacing.space2),
                )
            }
            metadata.updateIntervalHours?.let {
                Text(
                    text = stringResource(R.string.subscription_metadata_update_interval, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    modifier = Modifier.padding(top = Spacing.space2),
                )
            }
            metadata.announcement?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.space8),
                )
            }
        }
    }
}

@Composable
private fun SubscriptionCatalogControls(
    query: String,
    onQueryChange: (String) -> Unit,
    favoritesOnly: Boolean,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    sortOrder: SubscriptionSortOrder,
    onSortOrderChange: (SubscriptionSortOrder) -> Unit,
) {
    DetourInputField(
        value = query,
        onValueChange = onQueryChange,
        label = stringResource(R.string.subscription_search_label),
        placeholder = stringResource(R.string.subscription_search_hint),
        minHeight = 48.dp,
        maxHeight = 48.dp,
    )
    Spacer(Modifier.height(Spacing.space8))
    Row(Modifier.fillMaxWidth()) {
        CatalogControlButton(
            text = stringResource(R.string.subscription_filter_all),
            selected = !favoritesOnly,
            onClick = { onFavoritesOnlyChange(false) },
            modifier = Modifier.weight(1f),
        )
        CatalogControlButton(
            text = stringResource(R.string.subscription_filter_favorites),
            selected = favoritesOnly,
            onClick = { onFavoritesOnlyChange(true) },
            modifier = Modifier.weight(1f),
        )
    }
    Row(Modifier.fillMaxWidth()) {
        CatalogControlButton(
            text = stringResource(R.string.subscription_sort_default),
            selected = sortOrder == SubscriptionSortOrder.DEFAULT,
            onClick = { onSortOrderChange(SubscriptionSortOrder.DEFAULT) },
            modifier = Modifier.weight(1f),
        )
        CatalogControlButton(
            text = stringResource(R.string.subscription_sort_latency),
            selected = sortOrder == SubscriptionSortOrder.LATENCY,
            onClick = { onSortOrderChange(SubscriptionSortOrder.LATENCY) },
            modifier = Modifier.weight(1f),
        )
        CatalogControlButton(
            text = stringResource(R.string.subscription_sort_name),
            selected = sortOrder == SubscriptionSortOrder.NAME,
            onClick = { onSortOrderChange(SubscriptionSortOrder.NAME) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CatalogControlButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) c.accent else c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SubscriptionServerList(
    nodes: List<SubscriptionCatalogNode>,
    selectedNode: String?,
    selecting: Boolean,
    favoriteNodes: Set<String>,
    latencyByName: Map<String, Int>,
    latencyTestedNames: Set<String>,
    latencyErrorByName: Map<String, SubscriptionLatencyError>,
    onSelect: (String) -> Unit,
    onFavoriteChange: (String, Boolean) -> Unit,
) {
    DetourCard(Modifier.selectableGroup()) {
        nodes.forEachIndexed { index, node ->
            SubscriptionNodeRow(
                name = node.name,
                selected = node.name == selectedNode,
                enabled = !selecting,
                favorite = node.name in favoriteNodes,
                delayMs = latencyByName[node.name],
                latencyTested = node.name in latencyTestedNames,
                latencyError = latencyErrorByName[node.name],
                onSelect = { onSelect(node.name) },
                onFavoriteChange = { favorite -> onFavoriteChange(node.name, favorite) },
            )
            if (index < nodes.lastIndex) GroupDivider(startInset = ChoiceRowDividerInset)
        }
    }
}

@Composable
private fun SubscriptionNodeRow(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    favorite: Boolean,
    delayMs: Int?,
    latencyTested: Boolean,
    latencyError: SubscriptionLatencyError?,
    onSelect: () -> Unit,
    onFavoriteChange: (Boolean) -> Unit,
) {
    val c = detourColors
    val theme = LocalDetourTheme.current
    val favoriteDescription = stringResource(
        if (favorite) R.string.subscription_favorite_remove else R.string.subscription_favorite_add,
        name,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .detourSelectable(
                selected = selected,
                onClick = { if (enabled && !selected) onSelect() },
                idleColor = if (selected) c.accentSoft else Color.Transparent,
                pressedColor = if (selected) c.accentSoft else c.surfaceSelected,
                pressScale = Motion.PRESS_RADIO,
            )
            .heightIn(min = 56.dp)
            .padding(start = Spacing.space16, end = Spacing.space8, top = Spacing.space8, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected = selected)
        Column(
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (latencyError != null) {
                Text(
                    text = stringResource(R.string.subscription_latency_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = latencyBadColor(theme),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.space2),
                )
            }
        }
        if (delayMs != null || latencyTested) {
            val delayColor = if (latencyError != null) latencyBadColor(theme) else latencyColorFor(theme, delayMs)
            Text(
                text = delayMs?.let { stringResource(R.string.subscription_node_delay, it) } ?: "—",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = delayColor,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .padding(start = Spacing.space8)
                    .widthIn(min = 64.dp),
            )
        }
        TextButton(
            onClick = { onFavoriteChange(!favorite) },
            modifier = Modifier.semantics { contentDescription = favoriteDescription },
        ) {
            Text(
                text = if (favorite) "★" else "☆",
                style = MaterialTheme.typography.titleMedium,
                color = if (favorite) c.accent else c.textMuted,
            )
        }
        if (!enabled && selected) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(start = Spacing.space4)
                    .size(18.dp),
                strokeWidth = 2.dp,
                color = c.accent,
            )
        }
    }
}

@Composable
private fun SubscriptionNotice(
    text: String,
    error: Boolean = false,
    loading: Boolean = false,
) {
    val c = detourColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (error) c.errorSoft else c.surfaceSoft, AppShapes.small)
            .border(
                1.dp,
                if (error) c.error.copy(alpha = 0.32f) else c.border,
                AppShapes.small,
            )
            .padding(Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
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

internal fun shouldAutoSelectSubscriptionNode(status: SubscriptionSelectionStatus): Boolean =
    status == SubscriptionSelectionStatus.IDLE
