package dev.triplet.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.core.DpiDomainCatalog
import dev.triplet.app.core.DpiPreset

@Composable
fun DpiScreen(viewModel: DpiViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val c = detourColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val customVisibility = remember { MutableTransitionState(state.editingCustom) }
    customVisibility.targetState = state.editingCustom
    val autoVisibility = remember { MutableTransitionState(state.editingAuto) }
    autoVisibility.targetState = state.editingAuto
    val spatialMotionActive = scrollState.isScrollInProgress ||
        !customVisibility.isIdle || !autoVisibility.isIdle

    LaunchedEffect(viewModel) {
        viewModel.customSaved.collect {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.autoApplied.collect {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .detourHighRefresh(spatialMotionActive),
    ) {
        DetourBrandedHeader(stringResource(R.string.dpi_title), onBack)

        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space8))
            DetourFeatureSummary(
                iconRes = R.drawable.ic_dpi,
                title = stringResource(R.string.dpi_hint_title),
                subtitle = stringResource(R.string.dpi_hint),
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )

            Spacer(Modifier.height(Spacing.space12))
            DetourCard(Modifier.padding(horizontal = Spacing.space16).selectableGroup()) {
                ChoiceRow(
                    title = stringResource(R.string.preset_recommended),
                    selected = !state.editingCustom && !state.editingAuto &&
                        state.preset == DpiPreset.RECOMMENDED,
                    onClick = viewModel::chooseRecommended,
                )
                GroupDivider(startInset = ChoiceRowDividerInset)
                ChoiceRow(
                    title = stringResource(R.string.preset_auto),
                    subtitle = stringResource(R.string.dpi_auto_subtitle),
                    selected = state.editingAuto,
                    onClick = viewModel::editAuto,
                )
                GroupDivider(startInset = ChoiceRowDividerInset)
                ChoiceRow(
                    title = stringResource(R.string.preset_custom),
                    selected = state.editingCustom,
                    onClick = viewModel::editCustom,
                )
            }

            AnimatedVisibility(
                visibleState = autoVisibility,
                enter = autoEnterTransition(),
                exit = autoExitTransition(),
            ) {
                AutoStrategyPanel(state = state, viewModel = viewModel)
            }

            AnimatedVisibility(
                visibleState = customVisibility,
                enter = autoEnterTransition(),
                exit = autoExitTransition(),
            ) {
                Column {
                    Spacer(Modifier.height(Spacing.space16))
                    DetourInputField(
                        value = state.customField,
                        onValueChange = viewModel::setCustomField,
                        label = stringResource(R.string.dpi_custom_label),
                        placeholder = stringResource(R.string.dpi_custom_placeholder),
                        error = when {
                            state.customInvalid -> stringResource(R.string.dpi_custom_invalid)
                            state.saveState == DpiSaveState.ERROR -> stringResource(R.string.dpi_save_error)
                            else -> null
                        },
                        singleLine = false,
                        minHeight = 56.dp,
                        maxHeight = 104.dp,
                        maxLines = 3,
                        modifier = Modifier.padding(horizontal = Spacing.space16),
                    )
                    Spacer(Modifier.height(Spacing.space16))
                    DetourButton(
                        text = if (state.saveState == DpiSaveState.SAVING) {
                            stringResource(R.string.dpi_saving)
                        } else {
                            stringResource(R.string.btn_save)
                        },
                        onClick = viewModel::saveCustom,
                        enabled = state.canSaveCustom,
                        modifier = Modifier.padding(horizontal = Spacing.space16),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.space24))
        }
    }
}

@Composable
private fun AutoStrategyPanel(state: DpiUiState, viewModel: DpiViewModel) {
    val c = detourColors
    val controlsEnabled = state.autoRunState != DpiAutoRunState.RUNNING &&
        state.autoRunState != DpiAutoRunState.CANCELLING &&
        state.autoRunState != DpiAutoRunState.APPLYING

    Column {
        Spacer(Modifier.height(Spacing.space16))
        Text(
            text = stringResource(R.string.dpi_auto_targets),
            style = MaterialTheme.typography.titleSmall,
            color = c.textPrimary,
            modifier = Modifier.padding(horizontal = Spacing.space16),
        )
        Spacer(Modifier.height(Spacing.space8))
        DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
            DpiDomainCatalog.default.forEachIndexed { index, group ->
                AutoDomainRow(
                    title = domainGroupTitle(group.id),
                    subtitle = group.targets.joinToString(" · ") { it.host },
                    selected = group.id in state.selectedAutoGroups,
                    enabled = controlsEnabled,
                    onToggle = { viewModel.toggleAutoGroup(group.id) },
                )
                if (index != DpiDomainCatalog.default.lastIndex) {
                    GroupDivider(startInset = ChoiceRowDividerInset)
                }
            }
        }

        Spacer(Modifier.height(Spacing.space12))
        if (controlsEnabled) {
            DetourInputField(
                value = state.customAutoDomains,
                onValueChange = viewModel::setAutoCustomDomains,
                label = stringResource(R.string.dpi_auto_custom_domains),
                placeholder = stringResource(R.string.dpi_auto_custom_domains_placeholder),
                helper = stringResource(R.string.dpi_auto_custom_domains_hint),
                error = if (state.customAutoDomainsInvalid) {
                    stringResource(R.string.dpi_auto_custom_domains_invalid)
                } else {
                    null
                },
                singleLine = false,
                minHeight = 56.dp,
                maxHeight = 120.dp,
                maxLines = 4,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
        } else if (state.customAutoDomains.isNotBlank()) {
            Text(
                text = stringResource(R.string.dpi_auto_custom_domains),
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
            Text(
                text = state.customAutoDomains,
                style = MaterialTheme.typography.bodySmall,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4),
            )
        }

        Spacer(Modifier.height(Spacing.space12))
        if (!state.vpnIdle) {
            Text(
                text = stringResource(R.string.dpi_auto_stop_vpn),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
            Spacer(Modifier.height(Spacing.space8))
        }

        when (state.autoRunState) {
            DpiAutoRunState.RUNNING -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.space16),
                )
                Spacer(Modifier.height(Spacing.space8))
                Text(
                    text = stringResource(R.string.dpi_auto_testing),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
            }
            DpiAutoRunState.CANCELLING -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.space16),
                )
                Spacer(Modifier.height(Spacing.space8))
                Text(
                    text = stringResource(R.string.dpi_auto_stopping),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
            }
            DpiAutoRunState.ERROR -> {
                Text(
                    text = stringResource(R.string.dpi_auto_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.error,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
            }
            DpiAutoRunState.COMPLETE,
            DpiAutoRunState.APPLYING,
            -> AutoResult(state)
            DpiAutoRunState.IDLE -> Unit
        }

        Spacer(Modifier.height(Spacing.space12))
        if (state.autoRunState == DpiAutoRunState.RUNNING) {
            DetourButton(
                text = stringResource(R.string.dpi_auto_cancel),
                onClick = viewModel::cancelAutoTest,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
        } else {
            DetourButton(
                text = stringResource(R.string.dpi_auto_start),
                onClick = viewModel::startAutoTest,
                enabled = state.canRunAuto,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
        }

        if (state.canApplyAuto || state.autoRunState == DpiAutoRunState.APPLYING) {
            Spacer(Modifier.height(Spacing.space8))
            DetourButton(
                text = if (state.autoRunState == DpiAutoRunState.APPLYING) {
                    stringResource(R.string.dpi_auto_applying)
                } else {
                    stringResource(R.string.dpi_auto_apply)
                },
                onClick = viewModel::applyAuto,
                enabled = state.canApplyAuto,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
        }
    }
}

@Composable
private fun AutoResult(state: DpiUiState) {
    val c = detourColors
    val report = state.autoReport ?: return
    val winner = report.winner
    val text = when {
        report.allDirect -> stringResource(R.string.dpi_auto_direct_ok)
        winner != null -> stringResource(
            R.string.dpi_auto_found,
            report.problematicTargets.size,
            winner.candidate.args.joinToString(" "),
        )
        else -> stringResource(R.string.dpi_auto_not_found, report.problematicTargets.size)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (winner == null && !report.allDirect) c.error else c.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4),
    )
    if (
        winner != null && state.preset == DpiPreset.AUTO &&
        state.appliedAutoCandidateId == winner.candidate.id &&
        state.autoRunState != DpiAutoRunState.APPLYING
    ) {
        Text(
            text = stringResource(R.string.dpi_auto_applied),
            style = MaterialTheme.typography.bodySmall,
            color = c.activeStrong,
            modifier = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4),
        )
    }
}

@Composable
private fun AutoDomainRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val c = detourColors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) c.accentSoft else Color.Transparent)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        Column(Modifier.padding(start = Spacing.space12).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Spacing.space2),
            )
        }
    }
}

@Composable
private fun domainGroupTitle(id: String): String = when (id) {
    "youtube" -> stringResource(R.string.dpi_auto_youtube)
    "googlevideo" -> stringResource(R.string.dpi_auto_googlevideo)
    "discord" -> stringResource(R.string.dpi_auto_discord)
    "telegram" -> stringResource(R.string.dpi_auto_telegram)
    else -> id
}

private fun autoEnterTransition() = fadeIn(
    tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING),
) + expandVertically(
    animationSpec = tween(Motion.STATE_MS, easing = Motion.ENTER_EASING),
    expandFrom = Alignment.Top,
)

private fun autoExitTransition() = fadeOut(
    tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
) + shrinkVertically(
    animationSpec = tween(Motion.STATE_MS, easing = Motion.EXIT_EASING),
    shrinkTowards = Alignment.Top,
)
