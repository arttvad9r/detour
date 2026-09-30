package dev.detour.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.detour.app.R
import dev.detour.app.core.DpiProxyTestResultSummary
import dev.detour.app.core.DpiProxyTestRun
import dev.detour.app.core.DpiProxyTestStrategy
import dev.detour.app.core.DpiProxyTestStrategySelection
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
internal fun StrategySelectionAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (enabled) c.accent else c.textMuted,
        modifier = modifier
            .let { base ->
                if (enabled) {
                    base.detourClickable(
                        onClick = onClick,
                        pressedColor = c.accentSoft,
                    )
                } else {
                    base
                }
            }
            .padding(horizontal = Spacing.space8, vertical = Spacing.space8),
    )
}

@Composable
internal fun ProxyStrategyRow(
    strategy: DpiProxyTestStrategy,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val c = detourColors
    val interaction = if (enabled) {
        Modifier.detourToggleable(
            value = selected,
            onValueChange = { onToggle() },
            pressedColor = c.surfaceSelected,
        )
    } else {
        Modifier
    }
    Row(
        interaction
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = ProxyRowHorizontalPadding, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.dpi_proxy_test_strategy_number, strategy.referenceIndex),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            Text(
                text = strategy.command,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.space2),
            )
        }
        DetourSwitch(
            checked = selected,
            onCheckedChange = null,
            compact = true,
        )
    }
}

@Composable
internal fun ProxyHistoryRunRow(
    run: DpiProxyTestRun,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = detourColors
    val interaction = if (enabled) {
        Modifier.detourClickable(
            onClick = onClick,
            idleColor = if (selected) c.accentSoft else Color.Transparent,
            pressedColor = c.surfaceSelected,
        )
    } else {
        Modifier.background(if (selected) c.accentSoft else Color.Transparent)
    }
    Row(
        interaction
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = ProxyRowHorizontalPadding, vertical = ProxyRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        Column(
            Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        ) {
            Text(
                text = rememberRunTimestamp(run.createdAtEpochMs),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            Text(
                text = runHistorySummary(run),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.space2),
            )
        }
    }
}

@Composable
internal fun ProxyResultNavigationRow(
    result: DpiProxyTestResultSummary,
    rank: Int,
    best: Boolean,
    onClick: () -> Unit,
) {
    val c = detourColors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .detourClickable(
                onClick = onClick,
                idleColor = if (best) c.accentSoft else Color.Transparent,
                pressedColor = c.surfaceSelected,
                pressScale = Motion.PRESS_ROW,
            )
            .padding(horizontal = ProxyRowHorizontalPadding, vertical = ProxyRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(
                    if (best) c.accent else c.surfaceSoft,
                    AppShapes.extraSmall,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (best) c.onAccent else c.textSecondary,
            )
        }
        Column(
            Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        ) {
            Text(
                text = strategyTitle(result.strategy),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = compactResultSummary(result, best),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.space2),
            )
        }
        Chevron()
    }
}

@Composable
internal fun strategyTitle(strategy: DpiProxyTestStrategy): String =
    if (DpiProxyTestStrategySelection.isCustom(strategy)) {
        stringResource(R.string.dpi_proxy_test_custom_strategy)
    } else {
        stringResource(R.string.dpi_proxy_test_strategy_number, strategy.referenceIndex)
    }

@Composable
internal fun resultStatus(result: DpiProxyTestResultSummary): String = when {
    !result.backendStarted -> stringResource(R.string.dpi_proxy_test_backend_failed)
    result.fullCoverage -> stringResource(R.string.dpi_proxy_test_full_coverage)
    else -> stringResource(R.string.dpi_proxy_test_partial)
}

@Composable
private fun compactResultSummary(
    result: DpiProxyTestResultSummary,
    best: Boolean,
): String {
    val latency = result.medianLatencyMs?.let {
        stringResource(R.string.dpi_proxy_test_latency_short, it)
    } ?: stringResource(R.string.dpi_proxy_test_latency_unknown)
    return stringResource(
        if (best) {
            R.string.dpi_proxy_test_best_result_summary
        } else {
            R.string.dpi_proxy_test_compact_result_summary
        },
        resultStatus(result),
        result.fullyWorkingHosts,
        result.hostCount,
        latency,
    )
}

@Composable
internal fun runHistorySummary(run: DpiProxyTestRun): String {
    val hosts = run.results.firstOrNull()?.hostCount ?: 0
    return stringResource(
        R.string.dpi_proxy_test_history_summary,
        run.results.size,
        hosts,
    )
}

@Composable
internal fun rememberRunTimestamp(epochMs: Long): String = rememberSaveable(epochMs) {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = detourColors.textSecondary,
        modifier = Modifier.padding(
            start = Spacing.space16,
            end = Spacing.space16,
            bottom = Spacing.space8,
        ),
    )
}

@Composable
internal fun ProxySliderRow(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    valueSuffix: String = "",
) {
    val c = detourColors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ProxyRowHorizontalPadding, vertical = ProxyRowVerticalPadding),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            Text(
                text = "$value$valueSuffix",
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.space2),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = 0,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        )
    }
}
