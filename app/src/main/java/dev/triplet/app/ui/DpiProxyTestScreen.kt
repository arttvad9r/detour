package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.core.DpiProxyTestCatalog
import dev.triplet.app.core.DpiProxyTestConfig
import dev.triplet.app.core.DpiProxyTestStrategyResult
import kotlin.math.roundToInt

@Composable
internal fun DpiProxyTestScreen(
    viewModel: DpiViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.proxyTestState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = detourColors
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .detourHighRefresh(listState.isScrollInProgress),
        state = listState,
        contentPadding = PaddingValues(bottom = Spacing.space24),
    ) {
        item {
            DetourBrandedHeader(stringResource(R.string.dpi_proxy_test_title), onBack)
            Spacer(Modifier.height(Spacing.space8))
            DetourFeatureSummary(
                iconRes = R.drawable.ic_dpi,
                title = stringResource(R.string.dpi_proxy_test_summary_title),
                subtitle = stringResource(R.string.dpi_proxy_test_summary),
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
            Spacer(Modifier.height(Spacing.space16))
        }

        item {
            SectionLabel(stringResource(R.string.dpi_proxy_test_domains_title))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                DpiProxyTestCatalog.domainLists.forEachIndexed { index, list ->
                    val selected = list.id in state.selectedDomainIds
                    val rowModifier = if (state.running) {
                        Modifier
                    } else {
                        Modifier.detourToggleable(
                            value = selected,
                            onValueChange = { viewModel.toggleProxyDomain(list.id) },
                            pressedColor = c.surfaceSelected,
                        )
                    }
                    Row(
                        rowModifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = list.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.textPrimary,
                            )
                            Text(
                                text = stringResource(R.string.dpi_proxy_test_domain_count, list.hosts.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textMuted,
                            )
                        }
                        DetourSwitch(
                            checked = selected,
                            onCheckedChange = null,
                            compact = true,
                        )
                    }
                    if (index != DpiProxyTestCatalog.domainLists.lastIndex) {
                        GroupDivider(startInset = 16)
                    }
                }
            }
            Text(
                text = stringResource(R.string.dpi_proxy_test_selected_hosts, state.selectedHostCount),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                modifier = Modifier.padding(
                    start = Spacing.space20,
                    end = Spacing.space20,
                    top = Spacing.space8,
                ),
            )
            Spacer(Modifier.height(Spacing.space20))
        }

        item {
            SectionLabel(stringResource(R.string.dpi_proxy_test_parameters_title))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                ProxySliderRow(
                    title = stringResource(R.string.dpi_proxy_test_attempts),
                    value = state.attemptsPerHost,
                    range = DpiProxyTestConfig.ATTEMPTS_RANGE,
                    enabled = !state.running,
                    onValueChange = viewModel::setProxyAttempts,
                )
                GroupDivider(startInset = 16)
                ProxySliderRow(
                    title = stringResource(R.string.dpi_proxy_test_concurrency),
                    value = state.concurrency,
                    range = DpiProxyTestConfig.CONCURRENCY_RANGE,
                    enabled = !state.running,
                    onValueChange = viewModel::setProxyConcurrency,
                )
                GroupDivider(startInset = 16)
                ProxySliderRow(
                    title = stringResource(R.string.dpi_proxy_test_timeout),
                    value = state.timeoutSeconds,
                    range = DpiProxyTestConfig.TIMEOUT_RANGE,
                    enabled = !state.running,
                    valueSuffix = stringResource(R.string.dpi_proxy_test_seconds_short),
                    onValueChange = viewModel::setProxyTimeoutSeconds,
                )
            }
            Spacer(Modifier.height(Spacing.space16))
        }

        if (state.running || state.progress != null) {
            item {
                val progress = state.progress
                val fraction = if (progress == null) {
                    0f
                } else {
                    val completed = (progress.strategyIndex - 1) * progress.hostsTotal + progress.hostsCompleted
                    val total = progress.strategyTotal * progress.hostsTotal
                    completed.toFloat() / total.toFloat()
                }
                Column(Modifier.padding(horizontal = Spacing.space16)) {
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.space8))
                    Text(
                        text = if (progress == null) {
                            stringResource(R.string.dpi_proxy_test_starting)
                        } else {
                            stringResource(
                                R.string.dpi_proxy_test_progress,
                                progress.strategyIndex,
                                progress.strategyTotal,
                                progress.hostsCompleted,
                                progress.hostsTotal,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                    )
                }
                Spacer(Modifier.height(Spacing.space16))
            }
        }

        state.error?.let { error ->
            item {
                DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                    Text(
                        text = stringResource(
                            when (error) {
                                DpiProxyTestError.VPN_ACTIVE -> R.string.dpi_proxy_test_error_vpn
                                DpiProxyTestError.FAILED -> R.string.dpi_proxy_test_error_generic
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.error,
                        modifier = Modifier.padding(Spacing.space16),
                    )
                }
                Spacer(Modifier.height(Spacing.space16))
            }
        }

        if (state.cancelled) {
            item {
                Text(
                    text = stringResource(R.string.dpi_proxy_test_cancelled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
                Spacer(Modifier.height(Spacing.space16))
            }
        }

        item {
            DetourButton(
                text = if (state.running) {
                    stringResource(R.string.dpi_proxy_test_stop)
                } else {
                    stringResource(R.string.dpi_proxy_test_start)
                },
                onClick = {
                    if (state.running) viewModel.stopProxyTest()
                    else viewModel.startProxyTest(context)
                },
                enabled = state.running || state.canStart,
                style = if (state.running) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
            Spacer(Modifier.height(Spacing.space24))
        }

        if (state.completed) {
            item {
                SectionLabel(stringResource(R.string.dpi_proxy_test_results_title))
            }
            if (state.results.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dpi_proxy_test_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textMuted,
                        modifier = Modifier.padding(horizontal = Spacing.space16),
                    )
                }
            } else {
                items(
                    items = state.results,
                    key = { it.strategy.id },
                ) { result ->
                    ProxyResultCard(
                        result = result,
                        applying = state.applyingStrategyId == result.strategy.id,
                        applied = state.appliedStrategyId == result.strategy.id,
                        applyError = state.applyErrorStrategyId == result.strategy.id,
                        onApply = { viewModel.applyProxyStrategy(result.strategy.id) },
                    )
                    Spacer(Modifier.height(Spacing.space12))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = detourColors.textSecondary,
        modifier = Modifier.padding(
            start = Spacing.space20,
            end = Spacing.space20,
            bottom = Spacing.space8,
        ),
    )
}

@Composable
private fun ProxySliderRow(
    title: String,
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
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
            )
            Text(
                text = "$value$valueSuffix",
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProxyResultCard(
    result: DpiProxyTestStrategyResult,
    applying: Boolean,
    applied: Boolean,
    applyError: Boolean,
    onApply: () -> Unit,
) {
    val c = detourColors
    val status = when {
        !result.backendStarted -> stringResource(R.string.dpi_proxy_test_backend_failed)
        result.fullCoverage -> stringResource(R.string.dpi_proxy_test_full_coverage)
        else -> stringResource(R.string.dpi_proxy_test_partial)
    }
    DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
        Column(Modifier.padding(Spacing.space16)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.dpi_proxy_test_strategy_number,
                        result.strategy.referenceIndex,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (result.fullCoverage) c.activeStrong else c.textSecondary,
                )
            }
            Spacer(Modifier.height(Spacing.space8))
            Text(
                text = stringResource(
                    R.string.dpi_proxy_test_result_summary,
                    result.fullyWorkingHosts,
                    result.hosts.size,
                    result.totalSuccesses,
                    result.totalAttempts,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            result.medianLatencyMs?.let { latency ->
                Text(
                    text = stringResource(R.string.dpi_proxy_test_median, latency),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                )
            }
            Spacer(Modifier.height(Spacing.space8))
            Text(
                text = result.strategy.command,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = c.textMuted,
                maxLines = 3,
            )
            if (result.backendStarted && result.completed) {
                Spacer(Modifier.height(Spacing.space12))
                DetourButton(
                    text = when {
                        applying -> stringResource(R.string.dpi_proxy_test_applying)
                        applied -> stringResource(R.string.dpi_proxy_test_applied)
                        else -> stringResource(R.string.dpi_proxy_test_apply)
                    },
                    onClick = onApply,
                    enabled = !applying && !applied,
                    style = ButtonStyle.SECONDARY,
                    height = 44,
                )
                if (applyError) {
                    Text(
                        text = stringResource(R.string.dpi_proxy_test_apply_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.error,
                        modifier = Modifier.padding(top = Spacing.space8),
                    )
                }
            }
        }
    }
}
