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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.core.DpiPreset

@Composable
fun DpiScreen(viewModel: DpiViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val c = detourColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val customVisibility = remember { MutableTransitionState(state.editingCustom) }
    customVisibility.targetState = state.editingCustom
    val spatialMotionActive = scrollState.isScrollInProgress || !customVisibility.isIdle

    LaunchedEffect(viewModel) {
        viewModel.customSaved.collect {
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
                    selected = !state.editingCustom && state.preset == DpiPreset.RECOMMENDED,
                    onClick = viewModel::chooseRecommended,
                )
                GroupDivider(startInset = 56)
                ChoiceRow(
                    title = stringResource(R.string.preset_custom),
                    selected = state.editingCustom,
                    onClick = viewModel::editCustom,
                )
            }

            AnimatedVisibility(
                visibleState = customVisibility,
                enter = fadeIn(
                    tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING),
                ) + expandVertically(
                    animationSpec = tween(Motion.STATE_MS, easing = Motion.ENTER_EASING),
                    expandFrom = Alignment.Top,
                ),
                exit = fadeOut(
                    tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
                ) + shrinkVertically(
                    animationSpec = tween(Motion.STATE_MS, easing = Motion.EXIT_EASING),
                    shrinkTowards = Alignment.Top,
                ),
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
