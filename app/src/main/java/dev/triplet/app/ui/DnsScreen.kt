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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import dev.triplet.app.R
import dev.triplet.app.core.DnsOptions
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

private val DNS_LABELS = mapOf(
    "google" to R.string.dns_google,
    "cloudflare" to R.string.dns_cloudflare,
    "adguard" to R.string.dns_adguard,
)

@Composable
fun DnsScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState()
    val selectedDns = settings?.dnsId?.ifBlank { null } ?: "google"
    var customField by rememberSaveable(settings?.dnsCustom) {
        androidx.compose.runtime.mutableStateOf(settings?.dnsCustom ?: "")
    }
    var editingCustom by rememberSaveable(settings?.dnsId) {
        androidx.compose.runtime.mutableStateOf(settings?.dnsId == DnsOptions.CUSTOM)
    }
    val customInvalid = customField.isNotBlank() && !DnsOptions.isValid(customField)
    val scrollState = rememberScrollState()
    val customVisibility = remember { MutableTransitionState(editingCustom) }
    customVisibility.targetState = editingCustom
    val spatialMotionActive = scrollState.isScrollInProgress || !customVisibility.isIdle

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .detourHighRefresh(spatialMotionActive),
    ) {
        ScreenHeader(stringResource(R.string.dns_title), onBack)
        Spacer(Modifier.height(Spacing.space8))

        fun chooseKnown(id: String) {
            editingCustom = false
            if (selectedDns == id) return
            scope.launch {
                store.setDns(id, settings?.dnsCustom ?: "")
                VpnController.restartIfActive(ctx)
            }
        }

        fun editCustom() {
            if (editingCustom) return
            // Selecting the editor is not a configuration change. The active DNS
            // stays untouched until Save commits a valid custom resolver.
            editingCustom = true
        }

        DetourCard(Modifier.padding(horizontal = Spacing.space16).selectableGroup()) {
            DnsOptions.servers.forEach { (id, _) ->
                RadioRow(
                    title = stringResource(DNS_LABELS[id] ?: R.string.dns_custom),
                    selected = !editingCustom && selectedDns == id,
                    onClick = { chooseKnown(id) },
                )
                GroupDivider(startInset = 46)
            }
            RadioRow(
                title = stringResource(R.string.dns_custom),
                selected = editingCustom,
                onClick = ::editCustom,
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
                    value = customField,
                    onValueChange = { customField = it },
                    label = stringResource(R.string.dns_custom_label),
                    placeholder = stringResource(R.string.dns_placeholder),
                    helper = stringResource(R.string.dns_custom_hint_https),
                    error = if (customInvalid) stringResource(R.string.dns_invalid_https) else null,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
                Spacer(Modifier.height(Spacing.space16))
                DetourButton(
                    text = stringResource(R.string.btn_save),
                    onClick = {
                        scope.launch {
                            store.setDns(DnsOptions.CUSTOM, customField.trim())
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
