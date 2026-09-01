package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.triplet.app.R
import dev.triplet.app.TripletApp
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.SubscriptionNode
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState

private const val MAX_SUBSCRIPTION_NODE_ROWS = 64

private enum class SubscriptionSort { DEFAULT, LATENCY, NAME }

private data class SubscriptionDisplayNode(
    val name: String,
    val type: String,
    val alive: Boolean?,
    val delayMs: Int?,
)

@Composable
internal fun SubscriptionRuntimeSection(modifier: Modifier = Modifier) {
    val runtimeViewModel = viewModel<SubscriptionRuntimeViewModel>()
    val state by runtimeViewModel.uiState.collectAsStateWithLifecycle()
    val vpnState by VpnController.state.collectAsStateWithLifecycle()
    val connected = vpnState == VpnState.Active
    val context = LocalContext.current
    val store = remember(context) { (context.applicationContext as TripletApp).routesStore }
    val settings by store.settings.collectAsStateWithLifecycle()
    val activeUri = settings?.vlessKeys?.active?.uri.orEmpty()
    val subscriptionUrl = remember(activeUri) {
        ((VlessKeyParser.parse(activeUri) as? ParseResult.Ok)?.profile?.subscriptionUrl)
    }
    val cacheDir = remember(context) { context.cacheDir.absolutePath }
    val c = detourColors

    LaunchedEffect(subscriptionUrl, connected, cacheDir) {
        subscriptionUrl?.let { runtimeViewModel.bind(it, connected, cacheDir) }
    }

    LaunchedEffect(state.catalog, state.selectedNode) {
        if (state.catalog.isNotEmpty() && state.selectedNode !in state.catalog.map { it.name }) {
            runtimeViewModel.selectNode(state.catalog.first().name)
        }
    }

    var query by remember { mutableStateOf("") }
    var sortIndex by remember { mutableIntStateOf(0) }
    val sort = SubscriptionSort.entries[sortIndex]
    val runtimeByName = remember(state.provider.nodes) { state.provider.nodes.associateBy { it.name } }
    val displayNodes = remember(state.catalog, state.provider.nodes, query, sort) {
        val catalogNames = state.catalog.mapTo(linkedSetOf()) { it.name }
        val merged = buildList {
            state.catalog.forEach { catalogNode ->
                val runtime = runtimeByName[catalogNode.name]
                add(
                    SubscriptionDisplayNode(
                        name = catalogNode.name,
                        type = runtime?.type ?: catalogNode.type,
                        alive = runtime?.alive,
                        delayMs = runtime?.delayMs,
                    ),
                )
            }
            state.provider.nodes.forEach { runtime ->
                if (runtime.name !in catalogNames) {
                    add(
                        SubscriptionDisplayNode(
                            name = runtime.name,
                            type = runtime.type,
                            alive = runtime.alive,
                            delayMs = runtime.delayMs,
                        ),
                    )
                }
            }
        }
        val filtered = if (query.isBlank()) {
            merged
        } else {
            merged.filter { node ->
                node.name.contains(query, ignoreCase = true) ||
                    node.type.contains(query, ignoreCase = true)
            }
        }
        when (sort) {
            SubscriptionSort.DEFAULT -> filtered
            SubscriptionSort.LATENCY -> filtered.sortedWith(
                compareBy<SubscriptionDisplayNode> { it.delayMs == null }
                    .thenBy { it.delayMs ?: Int.MAX_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
            SubscriptionSort.NAME -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }

    val summary = when {
        state.catalogStatus == SubscriptionCatalogStatus.LOADING && displayNodes.isEmpty() ->
            stringResource(R.string.subscription_runtime_loading)
        state.catalogStatus == SubscriptionCatalogStatus.ERROR && displayNodes.isEmpty() ->
            stringResource(R.string.subscription_catalog_error)
        connected && state.provider.available -> stringResource(
            R.string.subscription_runtime_summary,
            state.provider.aliveNodes,
            state.provider.totalNodes,
        )
        else -> stringResource(R.string.subscription_runtime_disconnected)
    }

    Column(modifier.padding(horizontal = Spacing.space16)) {
        DetourFeatureSummary(
            iconRes = R.drawable.ic_globe,
            title = stringResource(R.string.subscription_runtime_title),
            subtitle = summary,
        )

        Spacer(Modifier.height(Spacing.space12))
        SelectedSubscriptionServerCard(
            selected = state.selectedNode,
            runtime = state.selectedNode?.let(runtimeByName::get),
            saving = state.selectionStatus == SubscriptionSelectionStatus.SAVING,
        )

        Spacer(Modifier.height(Spacing.space12))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(128) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = AppShapes.small,
            placeholder = {
                Text(
                    stringResource(R.string.subscription_search_hint),
                    color = c.textMuted,
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = c.textMuted,
                    modifier = Modifier.size(19.dp),
                )
            },
            colors = fieldColors(),
        )

        Spacer(Modifier.height(Spacing.space8))
        SegmentedControl(
            options = listOf(
                stringResource(R.string.subscription_sort_default),
                stringResource(R.string.subscription_sort_latency),
                stringResource(R.string.subscription_sort_name),
            ),
            selected = sortIndex,
            onSelect = { sortIndex = it },
        )

        Spacer(Modifier.height(Spacing.space12))
        when {
            state.catalogStatus == SubscriptionCatalogStatus.LOADING && displayNodes.isEmpty() -> {
                SubscriptionLoadingCard()
            }
            state.catalogStatus == SubscriptionCatalogStatus.ERROR && displayNodes.isEmpty() -> {
                SubscriptionNotice(
                    text = stringResource(R.string.subscription_catalog_error),
                    error = true,
                )
            }
            displayNodes.isNotEmpty() -> {
                SubscriptionServerList(
                    nodes = displayNodes.take(MAX_SUBSCRIPTION_NODE_ROWS),
                    selectedNode = state.selectedNode,
                    selecting = state.selectionStatus == SubscriptionSelectionStatus.SAVING,
                    onSelect = runtimeViewModel::selectNode,
                )
                val hiddenCount = (displayNodes.size - MAX_SUBSCRIPTION_NODE_ROWS).coerceAtLeast(0)
                if (hiddenCount > 0) {
                    Text(
                        text = stringResource(R.string.subscription_nodes_more, hiddenCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                        modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space8),
                    )
                }
            }
        }

        if (connected && state.status == SubscriptionRuntimeStatus.LOADING && !state.provider.available) {
            Spacer(Modifier.height(Spacing.space12))
            SubscriptionNotice(
                text = stringResource(R.string.subscription_runtime_loading),
                loading = true,
            )
        }

        if (connected && state.status == SubscriptionRuntimeStatus.ERROR) {
            Spacer(Modifier.height(Spacing.space12))
            SubscriptionNotice(
                text = stringResource(R.string.subscription_runtime_refresh_error),
                error = true,
            )
        }

        if (state.selectionStatus == SubscriptionSelectionStatus.ERROR) {
            Spacer(Modifier.height(Spacing.space12))
            SubscriptionNotice(
                text = stringResource(R.string.subscription_selection_error),
                error = true,
            )
            LaunchedEffect(state.selectionStatus) {
                runtimeViewModel.clearSelectionError()
            }
        }

        Spacer(Modifier.height(Spacing.space12))
        DetourButton(
            text = stringResource(
                if (state.status == SubscriptionRuntimeStatus.REFRESHING ||
                    state.catalogStatus == SubscriptionCatalogStatus.LOADING
                ) {
                    R.string.subscription_runtime_refreshing
                } else {
                    R.string.subscription_runtime_refresh
                },
            ),
            onClick = { runtimeViewModel.refresh(connected) },
            enabled = subscriptionUrl != null &&
                state.status != SubscriptionRuntimeStatus.REFRESHING &&
                state.catalogStatus != SubscriptionCatalogStatus.LOADING,
            style = ButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun SelectedSubscriptionServerCard(
    selected: String?,
    runtime: SubscriptionNode?,
    saving: Boolean,
) {
    val c = detourColors
    DetourCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetourIconTile(
                iconRes = R.drawable.ic_globe,
                selected = selected != null,
            )
            Column(
                modifier = Modifier
                    .padding(start = Spacing.space12)
                    .weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.subscription_selected_server),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textMuted,
                )
                Text(
                    text = selected ?: stringResource(R.string.subscription_select_server),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.space2),
                )
                runtime?.let { node ->
                    Text(
                        text = when {
                            !node.alive -> stringResource(R.string.subscription_node_offline)
                            node.delayMs != null -> stringResource(R.string.subscription_node_delay, node.delayMs)
                            else -> stringResource(R.string.subscription_node_online)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (node.alive) c.activeStrong else c.error,
                        modifier = Modifier.padding(top = Spacing.space2),
                    )
                }
            }
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = c.accent,
                )
            } else if (selected != null) {
                SelectionMark(selected = true)
            }
        }
    }
}

@Composable
private fun SubscriptionServerList(
    nodes: List<SubscriptionDisplayNode>,
    selectedNode: String?,
    selecting: Boolean,
    onSelect: (String) -> Unit,
) {
    DetourCard(Modifier.selectableGroup()) {
        nodes.forEachIndexed { index, node ->
            SubscriptionNodeRow(
                node = node,
                selected = node.name == selectedNode,
                enabled = !selecting,
                onSelect = { onSelect(node.name) },
            )
            if (index < nodes.lastIndex) GroupDivider(startInset = 68)
        }
    }
}

@Composable
private fun SubscriptionNodeRow(
    node: SubscriptionDisplayNode,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val c = detourColors
    val stateColor = when (node.alive) {
        true -> c.activeStrong
        false -> c.error
        null -> c.textMuted
    }
    val stateBackground = when (node.alive) {
        true -> c.activeSoft
        false -> c.errorSoft
        null -> c.surfaceSoft
    }
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
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(stateBackground, AppShapes.extraSmall)
                .border(
                    1.dp,
                    when (node.alive) {
                        true -> c.activeBorder
                        false -> c.error.copy(alpha = 0.30f)
                        null -> c.border
                    },
                    AppShapes.extraSmall,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_globe),
                contentDescription = null,
                tint = stateColor,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        ) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Spacing.space2),
            ) {
                Text(
                    text = node.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                )
                Text(
                    text = when {
                        node.alive == false -> stringResource(R.string.subscription_node_offline)
                        node.delayMs != null -> stringResource(R.string.subscription_node_delay, node.delayMs)
                        node.alive == true -> stringResource(R.string.subscription_node_online)
                        else -> stringResource(R.string.subscription_node_unknown)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = stateColor,
                )
            }
        }
        if (selected) {
            SelectionMark(selected = true, modifier = Modifier.padding(start = Spacing.space8))
        }
    }
}

@Composable
private fun SubscriptionLoadingCard() {
    SubscriptionNotice(
        text = stringResource(R.string.subscription_runtime_loading),
        loading = true,
    )
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
                painter = painterResource(if (error) R.drawable.ic_warning else R.drawable.ic_globe),
                contentDescription = null,
                tint = if (error) c.error else c.accent,
                modifier = Modifier.size(20.dp),
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
