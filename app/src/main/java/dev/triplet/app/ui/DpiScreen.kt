package dev.triplet.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.triplet.app.R
import dev.triplet.app.core.DpiArgs
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

@Composable
fun DpiScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState()
    var customField by rememberSaveable(settings?.dpiCustomArgs) {
        androidx.compose.runtime.mutableStateOf(settings?.dpiCustomArgs ?: "")
    }
    var editingCustom by rememberSaveable(settings?.preset) {
        androidx.compose.runtime.mutableStateOf(settings?.preset == DpiPreset.CUSTOM)
    }
    val customInvalid = customField.isNotBlank() && !DpiArgs.isValid(customField)

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(stringResource(R.string.dpi_title), onBack)
        Spacer(Modifier.height(Spacing.space8))

        fun chooseRecommended() {
            editingCustom = false
            if (settings?.preset == DpiPreset.RECOMMENDED) return
            scope.launch {
                store.setPreset(DpiPreset.RECOMMENDED)
                VpnController.restartIfActive(ctx)
            }
        }

        fun editCustom() {
            if (editingCustom) return
            // Editing must not mutate the live tunnel. Persist CUSTOM only after
            // the user saves a validated argument string below.
            editingCustom = true
        }

        DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
            RadioRow(
                title = stringResource(R.string.preset_recommended),
                selected = !editingCustom && settings?.preset == DpiPreset.RECOMMENDED,
                onClick = ::chooseRecommended,
            )
            GroupDivider(startInset = 46)
            RadioRow(
                title = stringResource(R.string.preset_custom),
                selected = editingCustom,
                onClick = ::editCustom,
            )
        }

        AnimatedVisibility(
            visible = editingCustom,
            enter = fadeIn(tween(Motion.CONTENT_IN_MS)) + expandVertically(
                animationSpec = spring(
                    dampingRatio = Motion.SPRING_DAMPING,
                    stiffness = Motion.SPRING_STIFFNESS_SOFT,
                ),
            ),
            exit = fadeOut(tween(Motion.CONTENT_OUT_MS)) + shrinkVertically(
                animationSpec = tween(Motion.STATE_MS),
            ),
        ) {
            Column {
                Spacer(Modifier.height(Spacing.space16))
                DetourInputField(
                    value = customField,
                    onValueChange = { value ->
                        customField = value.replace("\r", " ").replace("\n", " ")
                    },
                    label = stringResource(R.string.dpi_custom_label),
                    placeholder = stringResource(R.string.dpi_custom_placeholder),
                    helper = stringResource(R.string.dpi_custom_hint),
                    error = if (customInvalid) stringResource(R.string.dpi_custom_invalid) else null,
                    singleLine = false,
                    minHeight = 56.dp,
                    maxHeight = 104.dp,
                    maxLines = 3,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
                Spacer(Modifier.height(Spacing.space16))
                DetourButton(
                    text = stringResource(R.string.btn_save),
                    onClick = {
                        val value = customField.trim()
                        scope.launch {
                            // Store the validated draft before switching the active
                            // preset. If the process dies between writes, RECOMMENDED
                            // remains active rather than exposing an invalid CUSTOM.
                            store.setCustomArgs(value)
                            store.setPreset(DpiPreset.CUSTOM)
                            VpnController.restartIfActive(ctx)
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                    },
                    enabled = customField.isNotBlank() && !customInvalid,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
            }
        }

        Spacer(Modifier.height(Spacing.space24))
    }
}
