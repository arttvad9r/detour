package dev.detour.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.detour.app.R
import dev.detour.app.core.DpiProxyTestCatalog
import dev.detour.app.core.DpiProxyTestConfig
import dev.detour.app.core.DpiProxyTestResultSummary
import dev.detour.app.core.DpiProxyTestRun
import dev.detour.app.core.DpiProxyTestStrategy
import dev.detour.app.core.DpiProxyTestStrategySelection
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private enum class ProxyTestPage {
    MAIN,
    STRATEGIES,
    DOMAINS,
    PARAMETERS,
    HISTORY,
}

@Composable
internal fun DpiProxyTestScreen(
    viewModel: DpiViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageName by rememberSaveable { mutableStateOf(ProxyTestPage.MAIN.name) }
    val page = runCatching { ProxyTestPage.valueOf(pageName) }.getOrDefault(ProxyTestPage.MAIN)
    val backToMain = { pageName = ProxyTestPage.MAIN.name }

    BackHandler(enabled = page != ProxyTestPage.MAIN, onBack = backToMain)

    when (page) {
        ProxyTestPage.MAIN -> ProxyTestMainScreen(
            viewModel = viewModel,
            onBack = onBack,
            onOpenStrategies = { pageName = ProxyTestPage.STRATEGIES.name },
            onOpenDomains = { pageName = ProxyTestPage.DOMAINS.name },
            onOpenParameters = { pageName = ProxyTestPage.PARAMETERS.name },
            onOpenHistory = { pageName = ProxyTestPage.HISTORY.name },
            modifier = modifier,
        )

        ProxyTestPage.STRATEGIES -> ProxyTestStrategiesScreen(
            viewModel = viewModel,
            onBack = backToMain,
            modifier = modifier,
        )

        ProxyTestPage.DOMAINS -> ProxyTestDomainsScreen(
            viewModel = viewModel,
            onBack = backToMain,
            modifier = modifier,
        )

        ProxyTestPage.PARAMETERS -> ProxyTestParametersScreen(
            viewModel = viewModel,
            onBack = backToMain,
            modifier = modifier,
        )

        ProxyTestPage.HISTORY -> ProxyTestHistoryScreen(
            viewModel = viewModel,
            onBack = backToMain,
            modifier = modifier,
        )
    }
}

@Composable
private fun ProxyTestMainScreen(
    viewModel: DpiViewModel,
    onBack: () -> Unit,
    onOpenStrategies: () -> Unit,
    onOpenDomains: () -> Unit,
    onOpenParameters: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier,
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
            .imePadding()
            .detourHighRefresh(listState.isScrollInProgress),
        state = listState,
        contentPadding = PaddingValues(bottom = Spacing.space24),
    ) {
        item {
            DetourBrandedHeader(stringResource(R.string.dpi_proxy_test_title), onBack)
            Spacer(Modifier.height(Spacing.space12))
        }

        item {
            SectionLabel(stringResource(R.string.dpi_proxy_test_setup_title))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                DetourNavigationRow(
                    title = stringResource(R.string.dpi_proxy_test_strategies_title),
                    subtitle = stringResource(
                        R.string.dpi_proxy_test_selected_strategies,
                        state.selectedStrategyCount,
                    ),
                    iconRes = R.drawable.ic_dpi,
                    onClick = if (state.running) null else onOpenStrategies,
                )
                GroupDivider(startInset = NavigationRowDividerInset)
                DetourNavigationRow(
                    title = stringResource(R.string.dpi_proxy_test_domains_title),
                    subtitle = stringResource(
                        R.string.dpi_proxy_test_domains_summary,
                        state.selectedDomainIds.size,
                        state.selectedHostCount,
                    ),
                    iconRes = R.drawable.ic_globe,
                    onClick = if (state.running) null else onOpenDomains,
                )
                GroupDivider(startInset = NavigationRowDividerInset)
                DetourNavigationRow(
                    title = stringResource(R.string.dpi_proxy_test_parameters_title),
                    subtitle = stringResource(
                        R.string.dpi_proxy_test_parameters_summary,
                        state.attemptsPerHost,
                        state.concurrency,
                        state.timeoutSeconds,
                    ),
                    iconRes = R.drawable.ic_gear,
                    onClick = if (state.running) null else onOpenParameters,
                )
            }
            Spacer(Modifier.height(Spacing.space16))
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
                                DpiProxyTestError.HISTORY_SAVE -> R.string.dpi_proxy_test_error_history_save
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

        if (state.history.isNotEmpty()) {
            item {
                val selectedRun = state.selectedRun ?: state.history.first()
                SectionLabel(stringResource(R.string.dpi_proxy_test_history_title))
                DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                    DetourNavigationRow(
                        title = rememberRunTimestamp(selectedRun.createdAtEpochMs),
                        subtitle = runHistorySummary(selectedRun),
                        iconRes = R.drawable.ic_check,
                        onClick = if (state.running || state.applyingStrategyId != null) {
                            null
                        } else {
                            onOpenHistory
                        },
                    )
                }
                Spacer(Modifier.height(Spacing.space20))
            }
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
                    key = { "result-${state.selectedRunId}-${it.strategy.id}" },
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
        } else if (state.historyLoaded) {
            item {
                Text(
                    text = stringResource(R.string.dpi_proxy_test_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.space20),
                )
            }
        }
    }
}

@Composable
private fun ProxyTestStrategiesScreen(
    viewModel: DpiViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.proxyTestState.collectAsStateWithLifecycle()
    val c = detourColors
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .detourHighRefresh(listState.isScrollInProgress),
        state = listState,
        contentPadding = PaddingValues(bottom = Spacing.space24),
    ) {
        item {
            DetourBrandedHeader(stringResource(R.string.dpi_proxy_test_strategies_title), onBack)
            Spacer(Modifier.height(Spacing.space12))
        }

        item {
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.dpi_proxy_test_selected_strategies,
                            state.selectedStrategyCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }
                GroupDivider(startInset = 16)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StrategySelectionAction(
                        text = stringResource(R.string.dpi_proxy_test_select_all),
                        enabled = !state.running,
                        onClick = viewModel::selectAllProxyStrategies,
                        modifier = Modifier.weight(1f),
                    )
                    StrategySelectionAction(
                        text = stringResource(R.string.dpi_proxy_test_clear_selection),
                        enabled = !state.running,
                        onClick = viewModel::clearProxyStrategies,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.space16))
        }

        item {
            SectionLabel(stringResource(R.string.dpi_proxy_test_custom_title))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                Column(Modifier.padding(vertical = Spacing.space12)) {
                    DetourInputField(
                        value = state.customStrategyDraft,
                        onValueChange = viewModel::setProxyCustomStrategy,
                        label = stringResource(R.string.dpi_proxy_test_custom_label),
                        placeholder = stringResource(R.string.dpi_proxy_test_custom_placeholder),
                        error = if (state.customStrategyInvalid) {
                            stringResource(R.string.dpi_proxy_test_custom_invalid)
                        } else {
                            null
                        },
                        enabled = !state.running,
                        singleLine = false,
                        minHeight = 56.dp,
                        maxHeight = 120.dp,
                        maxLines = 4,
                        monospace = true,
                        modifier = Modifier.padding(horizontal = Spacing.space16),
                    )
                    Text(
                        text = stringResource(R.string.dpi_proxy_test_custom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                        modifier = Modifier.padding(
                            start = Spacing.space16,
                            end = Spacing.space16,
                            top = Spacing.space8,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.space20))
        }

        item {
            SectionLabel(stringResource(R.string.dpi_proxy_test_reference_strategies))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                Column {
                    DpiProxyTestCatalog.strategies.forEachIndexed { index, strategy ->
                        ProxyStrategyRow(
                            strategy = strategy,
                            selected = strategy.id in state.selectedReferenceStrategyIds,
                            enabled = !state.running,
                            onToggle = { viewModel.toggleProxyStrategy(strategy.id) },
                        )
                        if (index != DpiProxyTestCatalog.strategies.lastIndex) {
                            GroupDivider(startInset = 16)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyTestDomainsScreen(
    viewModel: DpiViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.proxyTestState.collectAsStateWithLifecycle()
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
            DetourBrandedHeader(stringResource(R.string.dpi_proxy_test_domains_title), onBack)
            Spacer(Modifier.height(Spacing.space12))
        }

        item {
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
        }
    }
}

@Composable
private fun ProxyTestParametersScreen(
    viewModel: DpiViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.proxyTestState.collectAsStateWithLifecycle()
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
            DetourBrandedHeader(stringResource(R.string.dpi_proxy_test_parameters_title), onBack)
            Spacer(Modifier.height(Spacing.space12))
        }

        item {
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                ProxySliderRow(
                    title = stringResource(R.string.dpi_proxy_test_attempts),
                    description = stringResource(R.string.dpi_proxy_test_attempts_hint),
                    value = state.attemptsPerHost,
                    range = DpiProxyTestConfig.ATTEMPTS_RANGE,
                    enabled = !state.running,
                    onValueChange = viewModel::setProxyAttempts,
                )
                GroupDivider(startInset = 16)
                ProxySliderRow(
                    title = stringResource(R.string.dpi_proxy_test_concurrency),
                    description = stringResource(R.string.dpi_proxy_test_concurrency_hint),
                    value = state.concurrency,
                    range = DpiProxyTestConfig.CONCURRENCY_RANGE,
                    enabled = !state.running,
                    onValueChange = viewModel::setProxyConcurrency,
                )
                GroupDivider(startInset = 16)
                ProxySliderRow(
                    title = stringResource(R.string.dpi_proxy_test_timeout),
                    description = stringResource(R.string.dpi_proxy_test_timeout_hint),
                    value = state.timeoutSeconds,
                    range = DpiProxyTestConfig.TIMEOUT_RANGE,
                    enabled = !state.running,
                    valueSuffix = stringResource(R.string.dpi_proxy_test_seconds_short),
                    onValueChange = viewModel::setProxyTimeoutSeconds,
                )
            }
        }
    }
}

@Composable
private fun ProxyTestHistoryScreen(
    viewModel: DpiViewModel,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.proxyTestState.collectAsStateWithLifecycle()
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
            DetourBrandedHeader(stringResource(R.string.dpi_proxy_test_history_title), onBack)
            Spacer(Modifier.height(Spacing.space12))
        }

        if (state.history.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dpi_proxy_test_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.space20),
                )
            }
        } else {
            item {
                DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                    Column {
                        state.history.forEachIndexed { index, run ->
                            ProxyHistoryRunRow(
                                run = run,
                                selected = state.selectedRunId == run.id,
                                enabled = !state.running && state.applyingStrategyId == null,
                                onClick = {
                                    viewModel.selectProxyRun(run.id)
                                    onBack()
                                },
                            )
                            if (index != state.history.lastIndex) {
                                GroupDivider(startInset = 16)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategySelectionAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
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
            .padding(Spacing.space12),
    )
}

@Composable
private fun ProxyStrategyRow(
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
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.dpi_proxy_test_strategy_number, strategy.referenceIndex),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
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
private fun ProxyHistoryRunRow(
    run: DpiProxyTestRun,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = detourColors
    val interaction = if (enabled) {
        Modifier.detourClickable(
            onClick = onClick,
            idleColor = if (selected) c.surfaceSelected else Color.Transparent,
            pressedColor = c.surfaceSelected,
        )
    } else {
        Modifier.background(if (selected) c.surfaceSelected else Color.Transparent)
    }
    Column(
        interaction
            .fillMaxWidth()
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
    ) {
        Text(
            text = rememberRunTimestamp(run.createdAtEpochMs),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
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

@Composable
private fun runHistorySummary(run: DpiProxyTestRun): String {
    val hosts = run.results.firstOrNull()?.hostCount ?: 0
    return stringResource(
        R.string.dpi_proxy_test_history_summary,
        run.results.size,
        hosts,
        run.config.attemptsPerHost,
    )
}

@Composable
private fun rememberRunTimestamp(epochMs: Long): String = rememberSaveable(epochMs) {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))
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
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
            modifier = Modifier.padding(top = Spacing.space4),
        )
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
    result: DpiProxyTestResultSummary,
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
                    text = if (DpiProxyTestStrategySelection.isCustom(result.strategy)) {
                        stringResource(R.string.dpi_proxy_test_custom_strategy)
                    } else {
                        stringResource(
                            R.string.dpi_proxy_test_strategy_number,
                            result.strategy.referenceIndex,
                        )
                    },
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
                    result.hostCount,
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
                maxLines = 4,
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
