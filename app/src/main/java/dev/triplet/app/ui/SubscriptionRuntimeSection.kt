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

private const val MAX_SUBSCRIPTION_NODE_ROWS = 128

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
        (VlessKeyParser.parse(activeUri) as? ParseResult.Ok)?.profile?.subscriptionUrl
    }
    val cacheDir = remember(context) { context.cacheDir.absolutePath }
    val c = detourColors

    LaunchedEffect(subscriptionUrl, connected, cacheDir) {
        subscriptionUrl?.let { runtimeViewModel.bind(it, connected, cacheDir) }
    }

    LaunchedEffect(state.catalog, state.selectedNode, state.selectionStatus) {
        if (
            state.catalog.isNotEmpty() &&
            state.selectionStatus != SubscriptionSelectionStatus.SAVING &&
            state.selectedNode !in state.catalog.map { it.name }
        ) {
            runtimeViewModel.selectNode(state.catalog.first().name)
        }
    }

    Column(modifier.padding(horizontal = Spacing.space16)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.subscription_runtime_title),
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { runtimeViewModel.refresh(connected) },
                enabled = subscriptionUrl != null &&
                    state.catalogStatus != SubscriptionCatalogStatus.LOADING &&
                    state.status != SubscriptionRuntimeStatus.REFRESHING,
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

        Spacer(Modifier.height(Spacing.space4))

        when {
            state.catalog.isNotEmpty() -> {
                SubscriptionServerList(
                    nodes = state.catalog.take(MAX_SUBSCRIPTION_NODE_ROWS),
                    selectedNode = state.selectedNode,
                    selecting = state.selectionStatus == SubscriptionSelectionStatus.SAVING,
                    onSelect = runtimeViewModel::selectNode,
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
            LaunchedEffect(state.selectionStatus) {
                runtimeViewModel.clearSelectionError()
            }
        }
    }
}

@Composable
private fun SubscriptionServerList(
    nodes: List<SubscriptionCatalogNode>,
    selectedNode: String?,
    selecting: Boolean,
    onSelect: (String) -> Unit,
) {
    DetourCard(Modifier.selectableGroup()) {
        nodes.forEachIndexed { index, node ->
            SubscriptionNodeRow(
                name = node.name,
                selected = node.name == selectedNode,
                enabled = !selecting,
                onSelect = { onSelect(node.name) },
            )
            if (index < nodes.lastIndex) GroupDivider(startInset = 60)
        }
    }
}

@Composable
private fun SubscriptionNodeRow(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val c = detourColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .detourSelectable(
                selected = selected,
                onClick = { if (enabled && !selected) onSelect() },
                idleColor = if (selected) c.accentSoft.copy(alpha = 0.48f) else Color.Transparent,
                pressedColor = if (selected) c.accentSoft else c.surfaceSelected,
                pressScale = Motion.PRESS_RADIO,
            )
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(if (selected) c.accentSoft else c.surfaceSoft, AppShapes.extraSmall)
                .border(
                    1.dp,
                    if (selected) c.accentBorder else c.border,
                    AppShapes.extraSmall,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_server),
                contentDescription = null,
                tint = if (selected) c.accent else c.textSecondary,
                modifier = Modifier.size(17.dp),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        )
        if (selected) {
            SelectionMark(selected = true, modifier = Modifier.padding(start = Spacing.space8))
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
