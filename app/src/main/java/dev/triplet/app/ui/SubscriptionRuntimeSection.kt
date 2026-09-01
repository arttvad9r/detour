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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.triplet.app.R
import dev.triplet.app.core.SubscriptionNode
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState

private const val MAX_SUBSCRIPTION_NODE_ROWS = 32

@Composable
internal fun SubscriptionRuntimeSection(modifier: Modifier = Modifier) {
    val runtimeViewModel = viewModel<SubscriptionRuntimeViewModel>()
    val state by runtimeViewModel.uiState.collectAsStateWithLifecycle()
    val vpnState by VpnController.state.collectAsStateWithLifecycle()
    val connected = vpnState == VpnState.Active
    val c = detourColors

    LaunchedEffect(connected) {
        if (connected) runtimeViewModel.load()
    }

    val summary = when {
        !connected -> stringResource(R.string.subscription_runtime_disconnected)
        state.status == SubscriptionRuntimeStatus.LOADING && !state.provider.available ->
            stringResource(R.string.subscription_runtime_loading)
        !state.provider.available -> stringResource(R.string.subscription_runtime_unavailable)
        else -> stringResource(
            R.string.subscription_runtime_summary,
            state.provider.aliveNodes,
            state.provider.totalNodes,
        )
    }

    Column(modifier.padding(horizontal = Spacing.space16)) {
        DetourFeatureSummary(
            iconRes = R.drawable.ic_globe,
            title = stringResource(R.string.subscription_runtime_title),
            subtitle = summary,
        )

        if (state.status == SubscriptionRuntimeStatus.LOADING && !state.provider.available) {
            Spacer(Modifier.height(Spacing.space12))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surfaceSoft, AppShapes.small)
                    .border(1.dp, c.border, AppShapes.small)
                    .padding(Spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = c.accent,
                )
                Text(
                    text = stringResource(R.string.subscription_runtime_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(start = Spacing.space12),
                )
            }
        }

        if (state.provider.available) {
            Spacer(Modifier.height(Spacing.space12))
            SubscriptionProviderCard(state)
        }

        if (state.status == SubscriptionRuntimeStatus.ERROR) {
            Spacer(Modifier.height(Spacing.space12))
            SubscriptionErrorNotice()
        }

        Spacer(Modifier.height(Spacing.space12))
        DetourButton(
            text = stringResource(
                if (state.status == SubscriptionRuntimeStatus.REFRESHING) {
                    R.string.subscription_runtime_refreshing
                } else {
                    R.string.subscription_runtime_refresh
                },
            ),
            onClick = runtimeViewModel::refresh,
            enabled = connected && state.status != SubscriptionRuntimeStatus.REFRESHING,
            style = ButtonStyle.SECONDARY,
        )
    }
}

@Composable
private fun SubscriptionProviderCard(state: SubscriptionRuntimeUiState) {
    val provider = state.provider
    val c = detourColors
    val visibleNodes = provider.nodes.take(MAX_SUBSCRIPTION_NODE_ROWS)

    DetourCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.space12),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.space16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.subscription_runtime_summary,
                            provider.aliveNodes,
                            provider.totalNodes,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary,
                    )
                    provider.updatedAt?.let { updatedAt ->
                        Text(
                            text = stringResource(R.string.subscription_runtime_updated, updatedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Spacing.space2),
                        )
                    }
                }
                SubscriptionAvailabilityBadge(
                    alive = provider.aliveNodes,
                    total = provider.totalNodes,
                )
            }

            if (visibleNodes.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.space12))
                GroupDivider(startInset = 16)
                visibleNodes.forEachIndexed { index, node ->
                    SubscriptionNodeRow(node)
                    if (index < visibleNodes.lastIndex) GroupDivider(startInset = 64)
                }
            }

            val hiddenCount = (provider.totalNodes - visibleNodes.size).coerceAtLeast(0)
            if (hiddenCount > 0) {
                Text(
                    text = stringResource(R.string.subscription_nodes_more, hiddenCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    modifier = Modifier.padding(
                        start = Spacing.space16,
                        end = Spacing.space16,
                        bottom = Spacing.space12,
                    ),
                )
            } else {
                Spacer(Modifier.height(Spacing.space4))
            }
        }
    }
}

@Composable
private fun SubscriptionAvailabilityBadge(alive: Int, total: Int) {
    val c = detourColors
    val healthy = total > 0 && alive > 0
    Box(
        modifier = Modifier
            .background(
                if (healthy) c.activeSoft else c.errorSoft,
                PillShape,
            )
            .border(
                1.dp,
                if (healthy) c.activeBorder else c.error.copy(alpha = 0.32f),
                PillShape,
            )
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
    ) {
        Text(
            text = "$alive/$total",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (healthy) c.activeStrong else c.error,
        )
    }
}

@Composable
private fun SubscriptionNodeRow(node: SubscriptionNode) {
    val c = detourColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (node.alive) c.activeSoft else c.errorSoft,
                    AppShapes.extraSmall,
                )
                .border(
                    1.dp,
                    if (node.alive) c.activeBorder else c.error.copy(alpha = 0.30f),
                    AppShapes.extraSmall,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_globe),
                contentDescription = null,
                tint = if (node.alive) c.activeStrong else c.error,
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (node.alive) c.activeStrong else c.error,
            modifier = Modifier.padding(start = Spacing.space8),
        )
    }
}

@Composable
private fun SubscriptionErrorNotice() {
    val c = detourColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.errorSoft, AppShapes.small)
            .border(1.dp, c.error.copy(alpha = 0.32f), AppShapes.small)
            .padding(Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = c.error,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.subscription_runtime_refresh_error),
            style = MaterialTheme.typography.bodySmall,
            color = c.textPrimary,
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        )
    }
}
