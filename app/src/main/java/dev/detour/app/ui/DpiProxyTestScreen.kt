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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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

private val ProxyHeaderGap = Spacing.space8
private val ProxySectionGap = Spacing.space12
private val ProxyRowHorizontalPadding = Spacing.space12
private val ProxyRowVerticalPadding = Spacing.space8
private const val ProxyPlainDividerInset = 12

private enum class ProxyTestPage {
    MAIN,
    STRATEGIES,
    DOMAINS,
    PARAMETERS,
    HISTORY,
    RESULTS,
    RESULT_DETAIL,
}

@Composable
internal fun DpiProxyTestScreen(
    viewModel: DpiViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageName by rememberSaveable { mutableStateOf(ProxyTestPage.MAIN.name) }
    var selectedResultId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailReturnPageName by rememberSaveable { mutableStateOf(ProxyTestPage.MAIN.name) }
    val page = runCatching { ProxyTestPage.valueOf(pageName) }.getOrDefault(ProxyTestPage.MAIN)
    val backToMain = { pageName = ProxyTestPage.MAIN.name }
    val backFromDetail = {
        pageName = runCatching {
            ProxyTestPage.valueOf(detailReturnPageName)
        }.getOrDefault(ProxyTestPage.MAIN).name
    }
    val navigateBack = {
        if (page == ProxyTestPage.RESULT_DETAIL) backFromDetail() else backToMain()
    }

    BackHandler(enabled = page != ProxyTestPage.MAIN, onBack = navigateBack)

    when (page) {
        ProxyTestPage.MAIN -> ProxyTestMainScreen(
            viewModel = viewModel,
            onBack = onBack,
            onOpenStrategies = { pageName = ProxyTestPage.STRATEGIES.name },
            onOpenDomains = { pageName = ProxyTestPage.DOMAINS.name },
            onOpenParameters = { pageName = ProxyTestPage.PARAMETERS.name },
            onOpenHistory = { pageName = ProxyTestPage.HISTORY.name },
            onOpenResults = { pageName = ProxyTestPage.RESULTS.name },
            onOpenResultDetail = { resultId ->
                selectedResultId = resultId
                detailReturnPageName = ProxyTestPage.MAIN.name
                pageName = ProxyTestPage.RESULT_DETAIL.name
            },
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

        ProxyTestPage.RESULTS -> ProxyTestResultsScreen(
            viewModel = viewModel,
            onBack = backToMain,
            onOpenDetail = { resultId ->
                selectedResultId = resultId
                detailReturnPageName = ProxyTestPage.RESULTS.name
                pageName = ProxyTestPage.RESULT_DETAIL.name
            },
            modifier = modifier,
        )

        ProxyTestPage.RESULT_DETAIL -> ProxyTestResultDetailScreen(
            viewModel = viewModel,
            resultId = selectedResultId,
            onBack = backFromDetail,
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
    onOpenResults: () -> Unit,
    onOpenResultDetail: (String) -> Unit,
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
            Spacer(Modifier.height(ProxyHeaderGap))
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
            Spacer(Modifier.height(ProxySectionGap))
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
                height = 48,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
            Spacer(Modifier.height(ProxySectionGap))
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
                    Spacer(Modifier.height(Spacing.space4))
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
                Spacer(Modifier.height(ProxySectionGap))
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
                        style = MaterialTheme.typography.bodySmall,
                        color = c.error,
                        modifier = Modifier.padding(Spacing.space12),
                    )
                }
                Spacer(Modifier.height(ProxySectionGap))
            }
        }

        if (state.cancelled) {
            item {
                Text(
                    text = stringResource(R.string.dpi_proxy_test_cancelled),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
                Spacer(Modifier.height(ProxySectionGap))
            }
        }

        if (state.history.isNotEmpty() || state.completed) {
            item {
                val selectedRun = state.selectedRun ?: state.history.firstOrNull()
                SectionLabel(stringResource(R.string.dpi_proxy_test_results_title))
                DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                    var hasPreviousRow = false

                    if (selectedRun != null) {
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
                        hasPreviousRow = true
                    }

                    if (state.completed) {
                        if (hasPreviousRow) GroupDivider(startInset = NavigationRowDividerInset)
                        if (state.results.isEmpty()) {
                            Text(
                                text = stringResource(R.string.dpi_proxy_test_no_results),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textMuted,
                                modifier = Modifier.padding(Spacing.space12),
                            )
                        } else {
                            val best = state.results.first()
                            ProxyResultNavigationRow(
                                result = best,
                                best = true,
                                onClick = { onOpenResultDetail(best.strategy.id) },
                            )
                            if (state.results.size > 1) {
                                GroupDivider(startInset = NavigationRowDividerInset)
                                DetourNavigationRow(
                                    title = stringResource(R.string.dpi_proxy_test_all_results),
                                    subtitle = stringResource(
                                        R.string.dpi_proxy_test_all_results_summary,
                                        state.results.size,
                                    ),
                                    iconRes = R.drawable.ic_dpi,
                                    onClick = onOpenResults,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(ProxySectionGap))
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
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(detourColors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .detourHighRefresh(listState.isScrollInProgress),
        state = listState,
        contentPadding = PaddingValues(bottom = Spacing.space24),
    ) {
        item {
            DetourBrandedHeader(stringResource(R.string.dpi_proxy_test_strategies_title), onBack)
            Spacer(Modifier.height(ProxyHeaderGap))
        }

        item {
            StrategySelectionToolbar(
                selectedCount = state.selectedStrategyCount,
                enabled = !state.running,
                onSelectAll = viewModel::selectAllProxyStrategies,
                onClear = viewModel::clearProxyStrategies,
            )
            Spacer(Modifier.height(ProxySectionGap))
        }

        item {
            SectionLabel(stringResource(R.string.dpi_proxy_test_custom_title))
            DetourInputField(
                value = state.customStrategyDraft,
                onValueChange = viewModel::setProxyCustomStrategy,
                label = stringResource(R.string.dpi_proxy_test_custom_label),
                placeholder = stringResource(R.string.dpi_proxy_test_custom_placeholder),
                helper = stringResource(R.string.dpi_proxy_test_custom_hint),
                error = if (state.customStrategyInvalid) {
                    stringResource(R.string.dpi_proxy_test_custom_invalid)
                } else {
                    null
                },
                enabled = !state.running,
                singleLine = false,
                minHeight = 48.dp,
                maxHeight = 88.dp,
                maxLines = 3,
                monospace = true,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
            Spacer(Modifier.height(ProxySectionGap))
        }

        item {
            SectionLabel(stringResource(R.string.dpi_proxy_test_reference_strategies))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                DpiProxyTestCatalog.strategies.forEachIndexed { index, strategy ->
                    ProxyStrategyRow(
                        strategy = strategy,
                        selected = strategy.id in state.selectedReferenceStrategyIds,
                        enabled = !state.running,
                        onToggle = { viewModel.toggleProxyStrategy(strategy.id) },
                    )
                    if (index != DpiProxyTestCatalog.strategies.lastIndex) {
                        GroupDivider(startInset = ProxyPlainDividerInset)
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategySelectionToolbar(
    selectedCount: Int,
    enabled: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    val c = detourColors
    DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = ProxyRowHorizontalPadding, vertical = Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dpi_proxy_test_selected_strategies, selectedCount),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.weight(1f),
            )
            StrategySelectionAction(
                text = stringResource(R.string.dpi_proxy_test_select_all_short),
                enabled = enabled,
                onClick = onSelectAll,
            )
            StrategySelectionAction(
                text = stringResource(R.string.dpi_proxy_test_clear_selection_short),
                enabled = enabled,
                onClick = onClear,
            )
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
            Spacer(Modifier.height(ProxyHeaderGap))
        }

        item {
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                Text(
                    text = stringResource(R.string.dpi_proxy_test_selected_hosts, state.selectedHostCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(
                        horizontal = ProxyRowHorizontalPadding,
                        vertical = ProxyRowVerticalPadding,
                    ),
                )
                GroupDivider(startInset = ProxyPlainDividerInset)
                DpiProxyTestCatalog.domainLists.forEachIndexed { index, list ->
                    val selected = list.id in state.selectedDomainIds
                    ProxyDomainRow(
                        title = list.displayName,
                        hostCount = list.hosts.size,
                        selected = selected,
                        enabled = !state.running,
                        onToggle = { viewModel.toggleProxyDomain(list.id) },
                    )
                    if (index != DpiProxyTestCatalog.domainLists.lastIndex) {
                        GroupDivider(startInset = ProxyPlainDividerInset)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyDomainRow(
    title: String,
    hostCount: Int,
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
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            Text(
                text = stringResource(R.string.dpi_proxy_test_domain_count, hostCount),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
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
            Spacer(Modifier.height(ProxyHeaderGap))
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
                GroupDivider(startInset = ProxyPlainDividerInset)
                ProxySliderRow(
                    title = stringResource(R.string.dpi_proxy_test_concurrency),
                    description = stringResource(R.string.dpi_proxy_test_concurrency_hint),
                    value = state.concurrency,
                    range = DpiProxyTestConfig.CONCURRENCY_RANGE,
                    enabled = !state.running,
                    onValueChange = viewModel::setProxyConcurrency,
                )
                GroupDivider(startInset = ProxyPlainDividerInset)
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
            Spacer(Modifier.height(ProxyHeaderGap))
        }

        if (state.history.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dpi_proxy_test_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
            }
        } else {
            item {
                DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
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
                            GroupDivider(startInset = ChoiceRowDividerInset)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyTestResultsScreen(
    viewModel: DpiViewModel,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
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
            DetourBrandedHeader(stringResource(R.string.dpi_proxy_test_all_results), onBack)
            Spacer(Modifier.height(ProxyHeaderGap))
        }

        if (state.results.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dpi_proxy_test_no_results),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
            }
        } else {
            item {
                DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                    state.results.forEachIndexed { index, result ->
                        ProxyResultNavigationRow(
                            result = result,
                            best = index == 0,
                            onClick = { onOpenDetail(result.strategy.id) },
                        )
                        if (index != state.results.lastIndex) {
                            GroupDivider(startInset = NavigationRowDividerInset)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyTestResultDetailScreen(
    viewModel: DpiViewModel,
    resultId: String?,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.proxyTestState.collectAsStateWithLifecycle()
    val result = state.results.firstOrNull { it.strategy.id == resultId }
    val c = detourColors
    val listState = rememberLazyListState()
    val title = result?.let { strategyTitle(it.strategy) }
        ?: stringResource(R.string.dpi_proxy_test_result_details)

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
            DetourBrandedHeader(title, onBack)
            Spacer(Modifier.height(ProxyHeaderGap))
        }

        if (result == null) {
            item {
                Text(
                    text = stringResource(R.string.dpi_proxy_test_no_results),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
            }
        } else {
            item {
                SectionLabel(stringResource(R.string.dpi_proxy_test_result_details))
                DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                    Column(Modifier.padding(Spacing.space12)) {
                        Text(
                            text = resultStatus(result),
                            style = MaterialTheme.typography.titleSmall,
                            color = when {
                                !result.backendStarted -> c.error
                                result.fullCoverage -> c.activeStrong
                                else -> c.textSecondary
                            },
                        )
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
                            modifier = Modifier.padding(top = Spacing.space4),
                        )
                        result.medianLatencyMs?.let { latency ->
                            Text(
                                text = stringResource(R.string.dpi_proxy_test_median, latency),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textMuted,
                                modifier = Modifier.padding(top = Spacing.space2),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(ProxySectionGap))
            }

            item {
                SectionLabel(stringResource(R.string.dpi_proxy_test_command_title))
                DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                    Text(
                        text = result.strategy.command,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = c.textSecondary,
                        modifier = Modifier.padding(Spacing.space12),
                    )
                }
                Spacer(Modifier.height(ProxySectionGap))
            }

            if (result.backendStarted && result.completed) {
                item {
                    val applying = state.applyingStrategyId == result.strategy.id
                    val applied = state.appliedStrategyId == result.strategy.id
                    DetourButton(
                        text = when {
                            applying -> stringResource(R.string.dpi_proxy_test_applying)
                            applied -> stringResource(R.string.dpi_proxy_test_applied)
                            else -> stringResource(R.string.dpi_proxy_test_apply)
                        },
                        onClick = { viewModel.applyProxyStrategy(result.strategy.id) },
                        enabled = !applying && !applied,
                        height = 48,
                        modifier = Modifier.padding(horizontal = Spacing.space16),
                    )
                    if (state.applyErrorStrategyId == result.strategy.id) {
                        Text(
                            text = stringResource(R.string.dpi_proxy_test_apply_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.error,
                            modifier = Modifier.padding(
                                start = Spacing.space16,
                                end = Spacing.space16,
                                top = Spacing.space8,
                            ),
                        )
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
private fun ProxyResultNavigationRow(
    result: DpiProxyTestResultSummary,
    best: Boolean,
    onClick: () -> Unit,
) {
    DetourNavigationRow(
        title = strategyTitle(result.strategy),
        subtitle = compactResultSummary(result, best),
        iconRes = R.drawable.ic_dpi,
        onClick = onClick,
        selectedBackground = best,
    )
}

@Composable
private fun strategyTitle(strategy: DpiProxyTestStrategy): String =
    if (DpiProxyTestStrategySelection.isCustom(strategy)) {
        stringResource(R.string.dpi_proxy_test_custom_strategy)
    } else {
        stringResource(R.string.dpi_proxy_test_strategy_number, strategy.referenceIndex)
    }

@Composable
private fun resultStatus(result: DpiProxyTestResultSummary): String = when {
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
            start = Spacing.space16,
            end = Spacing.space16,
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
