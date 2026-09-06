package dev.detour.app.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.detour.app.R
import dev.detour.app.DetourApp
import dev.detour.app.core.ParseResult
import dev.detour.app.core.VlessKeyParser
import dev.detour.app.vpn.VpnController
import dev.detour.app.vpn.VpnState

private const val MAX_SUBSCRIPTION_NODE_ROWS = 256

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
    val c = detourColors

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
                onClick = { runtimeViewModel.refresh(connected) },
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

        when {
            state.catalog.isNotEmpty() -> {
                SubscriptionServerList(
                    nodes = state.catalog.take(MAX_SUBSCRIPTION_NODE_ROWS),
                    selectedNode = state.selectedNode,
                    selecting = state.selectionStatus == SubscriptionSelectionStatus.SAVING,
                    latencyByName = latencyByName,
                    latencyTestedNames = latencyTestedNames,
                    latencyErrorByName = state.latencyErrorByName,
                    onSelect = { name -> runtimeViewModel.selectNode(name, persistSelectedNode) },
                )
                val hiddenCount = (state.catalog.size - MAX_SUBSCRIPTION_NODE_ROWS).coerceAtLeast(0)
                if (hiddenCount > 0) {
                    Text(
                        text = stringResource(R.string.subscription_nodes_more, hiddenCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                        modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space8),
                    )
                }
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
private fun SubscriptionServerList(
    nodes: List<SubscriptionCatalogNode>,
    selectedNode: String?,
    selecting: Boolean,
    latencyByName: Map<String, Int>,
    latencyTestedNames: Set<String>,
    latencyErrorByName: Map<String, SubscriptionLatencyError>,
    onSelect: (String) -> Unit,
) {
    DetourCard(Modifier.selectableGroup()) {
        nodes.forEachIndexed { index, node ->
            SubscriptionNodeRow(
                name = node.name,
                selected = node.name == selectedNode,
                enabled = !selecting,
                delayMs = latencyByName[node.name],
                latencyTested = node.name in latencyTestedNames,
                latencyError = latencyErrorByName[node.name],
                onSelect = { onSelect(node.name) },
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
    delayMs: Int?,
    latencyTested: Boolean,
    latencyError: SubscriptionLatencyError?,
    onSelect: () -> Unit,
) {
    val c = detourColors
    val theme = LocalDetourTheme.current
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
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
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
        if (!enabled && selected) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(start = Spacing.space8)
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
