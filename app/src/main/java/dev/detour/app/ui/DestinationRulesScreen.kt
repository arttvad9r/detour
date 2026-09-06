package dev.detour.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.detour.app.R
import dev.detour.app.core.AppRoute
import dev.detour.app.core.DestinationRule
import dev.detour.app.core.DestinationRuleType
import dev.detour.app.core.DestinationRules

internal fun destinationRuleTypeLabelRes(type: DestinationRuleType): Int = when (type) {
    DestinationRuleType.DOMAIN -> R.string.destination_rules_type_domain
    DestinationRuleType.DOMAIN_SUFFIX -> R.string.destination_rules_type_suffix
    DestinationRuleType.IP_CIDR -> R.string.destination_rules_type_cidr
}

internal fun destinationRuleTypeSubtitleRes(type: DestinationRuleType): Int = when (type) {
    DestinationRuleType.DOMAIN -> R.string.destination_rules_type_domain_sub
    DestinationRuleType.DOMAIN_SUFFIX -> R.string.destination_rules_type_suffix_sub
    DestinationRuleType.IP_CIDR -> R.string.destination_rules_type_cidr_sub
}

internal fun destinationRuleRouteLabelRes(route: AppRoute): Int = when (route) {
    AppRoute.DIRECT -> R.string.route_direct
    AppRoute.VPN -> R.string.route_vpn
    AppRoute.DPI -> R.string.route_dpi
}

@Composable
fun DestinationRulesScreen(
    viewModel: DestinationRulesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val c = detourColors
    val scrollState = rememberScrollState()
    var type by rememberSaveable { mutableStateOf(DestinationRuleType.DOMAIN) }
    var route by rememberSaveable { mutableStateOf(AppRoute.VPN) }
    var value by rememberSaveable { mutableStateOf("") }

    val candidate = remember(type, value, route) {
        DestinationRules.create(type, value, route)
    }
    val duplicate = candidate != null && state.rules.any {
        it.type == candidate.type && it.value == candidate.value
    }
    val localInvalid = value.isNotBlank() && candidate == null

    LaunchedEffect(type, route, value) {
        viewModel.clearError()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .detourHighRefresh(scrollState.isScrollInProgress),
    ) {
        DetourBrandedHeader(stringResource(R.string.destination_rules_title), onBack)

        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space8))
            DetourFeatureSummary(
                iconRes = R.drawable.ic_globe,
                title = stringResource(R.string.destination_rules_info_title),
                subtitle = stringResource(R.string.destination_rules_info),
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )

            Spacer(Modifier.height(Spacing.space16))
            Text(
                text = stringResource(R.string.destination_rules_type),
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.space20),
            )
            Spacer(Modifier.height(Spacing.space8))
            DetourCard(
                Modifier
                    .padding(horizontal = Spacing.space16)
                    .selectableGroup(),
            ) {
                DestinationRuleType.entries.forEachIndexed { index, item ->
                    ChoiceRow(
                        title = stringResource(destinationRuleTypeLabelRes(item)),
                        subtitle = stringResource(destinationRuleTypeSubtitleRes(item)),
                        selected = type == item,
                        onClick = { type = item },
                    )
                    if (index < DestinationRuleType.entries.lastIndex) {
                        GroupDivider(startInset = ChoiceRowDividerInset)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.space16))
            DetourInputField(
                value = value,
                onValueChange = { value = it },
                label = stringResource(R.string.destination_rules_value),
                placeholder = stringResource(
                    if (type == DestinationRuleType.IP_CIDR) {
                        R.string.destination_rules_value_cidr_hint
                    } else {
                        R.string.destination_rules_value_domain_hint
                    },
                ),
                error = when {
                    localInvalid -> stringResource(R.string.destination_rules_invalid)
                    duplicate -> stringResource(R.string.destination_rules_duplicate)
                    else -> null
                },
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )

            Spacer(Modifier.height(Spacing.space16))
            Text(
                text = stringResource(R.string.destination_rules_route),
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.space20),
            )
            Spacer(Modifier.height(Spacing.space8))
            SegmentedControl(
                options = AppRoute.entries.map { stringResource(destinationRuleRouteLabelRes(it)) },
                selected = AppRoute.entries.indexOf(route),
                onSelect = { index -> route = AppRoute.entries[index] },
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )

            val persistedError = when (state.error) {
                DestinationRulesError.DUPLICATE -> R.string.destination_rules_duplicate
                DestinationRulesError.LIMIT -> R.string.destination_rules_limit
                DestinationRulesError.SAVE -> R.string.destination_rules_save_error
                null -> null
            }
            if (persistedError != null) {
                Spacer(Modifier.height(Spacing.space8))
                Text(
                    text = stringResource(persistedError),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.error,
                    modifier = Modifier.padding(horizontal = Spacing.space20),
                )
            }

            Spacer(Modifier.height(Spacing.space16))
            DetourButton(
                text = stringResource(R.string.destination_rules_add),
                onClick = {
                    candidate?.let(viewModel::addRule)
                    if (candidate != null && !duplicate) value = ""
                },
                enabled = candidate != null && !duplicate && !state.saving,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )

            Spacer(Modifier.height(Spacing.space24))
            Text(
                text = stringResource(R.string.destination_rules_saved),
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.space20),
            )
            Spacer(Modifier.height(Spacing.space8))

            if (state.rules.isEmpty()) {
                Text(
                    text = stringResource(R.string.destination_rules_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space12),
                )
            } else {
                DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                    state.rules.forEachIndexed { index, rule ->
                        DestinationRuleRow(
                            rule = rule,
                            saving = state.saving,
                            onDelete = { viewModel.deleteRule(rule) },
                        )
                        if (index < state.rules.lastIndex) {
                            GroupDivider(startInset = Spacing.space16.value.toInt())
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.space24))
        }
    }
}

@Composable
private fun DestinationRuleRow(
    rule: DestinationRule,
    saving: Boolean,
    onDelete: () -> Unit,
) {
    val c = detourColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.space16,
                end = Spacing.space8,
                top = Spacing.space12,
                bottom = Spacing.space12,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = rule.value,
                style = MaterialTheme.typography.bodyLarge,
                color = c.textPrimary,
            )
            Spacer(Modifier.height(Spacing.space2))
            Text(
                text = "${stringResource(destinationRuleTypeLabelRes(rule.type))} · " +
                    stringResource(destinationRuleRouteLabelRes(rule.route)),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
        DetourIconButton(
            onClick = { if (!saving) onDelete() },
            size = 44,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.destination_rules_delete_cd),
                tint = if (saving) c.textMuted else c.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
