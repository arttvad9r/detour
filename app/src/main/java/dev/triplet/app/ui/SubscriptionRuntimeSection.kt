package dev.triplet.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.core.SubscriptionNode
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState

private const val MAX_SUBSCRIPTION_NODE_ROWS = 32

@Composable
internal fun SubscriptionRuntimeSection(modifier: Modifier = Modifier) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<SubscriptionRuntimeViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vpnState by VpnController.state.collectAsStateWithLifecycle()
    val connected = vpnState == VpnState.Active

    LaunchedEffect(connected) {
        if (connected) viewModel.load()
    }

    val c = detourColors
    Column(modifier.padding(horizontal = Spacing.space16)) {
        Text(
            text = stringResource(R.string.subscription_runtime_title),
            style = MaterialTheme.typography.titleSmall,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(Spacing.space8))
        DetourCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
            ) {
                when {
                    !connected -> Text(
                        text = stringResource(R.string.subscription_runtime_disconnected),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                    state.status == SubscriptionRuntimeStatus.LOADING && !state.provider.available -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = c.accent,
                        )
                        Spacer(Modifier.size(Spacing.space8))
                        Text(
                            text = stringResource(R.string.subscription_runtime_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                    !state.provider.available -> Text(
                        text = stringResource(R.string.subscription_runtime_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                    else -> SubscriptionProviderContent(state)
                }

                if (state.status == SubscriptionRuntimeStatus.ERROR) {
                    Spacer(Modifier.height(Spacing.space8))
                    Text(
                        text = stringResource(R.string.subscription_runtime_refresh_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.error,
                    )
                }

                Spacer(Modifier.height(Spacing.space8))
                TextButton(
                    onClick = viewModel::refresh,
                    enabled = connected && state.status != SubscriptionRuntimeStatus.REFRESHING,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    if (state.status == SubscriptionRuntimeStatus.REFRESHING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(Spacing.space8))
                    }
                    Text(
                        stringResource(
                            if (state.status == SubscriptionRuntimeStatus.REFRESHING) {
                                R.string.subscription_runtime_refreshing
                            } else {
                                R.string.subscription_runtime_refresh
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionProviderContent(state: SubscriptionRuntimeUiState) {
    val c = detourColors
    val provider = state.provider
    Text(
        text = stringResource(
            R.string.subscription_runtime_summary,
            provider.aliveNodes,
            provider.totalNodes,
        ),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = c.textPrimary,
    )
    provider.updatedAt?.let { updatedAt ->
        Text(
            text = stringResource(R.string.subscription_runtime_updated, updatedAt),
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.space2),
        )
    }

    val visibleNodes = provider.nodes.take(MAX_SUBSCRIPTION_NODE_ROWS)
    if (visibleNodes.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.space8))
        visibleNodes.forEachIndexed { index, node ->
            if (index > 0) GroupDivider(startInset = 0)
            SubscriptionNodeRow(node)
        }
    }
    val hiddenCount = (provider.totalNodes - visibleNodes.size).coerceAtLeast(0)
    if (hiddenCount > 0) {
        Text(
            text = stringResource(R.string.subscription_nodes_more, hiddenCount),
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
            modifier = Modifier.padding(top = Spacing.space8),
        )
    }
}

@Composable
private fun SubscriptionNodeRow(node: SubscriptionNode) {
    val c = detourColors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.space8),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = node.type,
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val status = when {
            !node.alive -> stringResource(R.string.subscription_node_offline)
            node.delayMs != null -> stringResource(R.string.subscription_node_delay, node.delayMs)
            else -> stringResource(R.string.subscription_node_online)
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (node.alive) c.accent else c.error,
            maxLines = 1,
        )
    }
}
