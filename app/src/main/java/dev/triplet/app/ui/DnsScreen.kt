package dev.triplet.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.core.DnsOptions

private val DNS_LABELS = mapOf(
    "google" to R.string.dns_google,
    "cloudflare" to R.string.dns_cloudflare,
    "adguard" to R.string.dns_adguard,
)

private val DNS_SUBTITLES = mapOf(
    "google" to R.string.dns_google_subtitle,
    "cloudflare" to R.string.dns_cloudflare_subtitle,
    "adguard" to R.string.dns_adguard_subtitle,
)

private val DNS_ICONS = mapOf(
    "google" to R.drawable.ic_globe,
    "cloudflare" to R.drawable.ic_globe,
    "adguard" to R.drawable.ic_lock,
)

@Composable
fun DnsScreen(viewModel: DnsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
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
        modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .detourHighRefresh(spatialMotionActive),
    ) {
        DetourBrandedHeader(stringResource(R.string.dns_title), onBack)

        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space8))
            DetourCard(
                Modifier
                    .padding(horizontal = Spacing.space16)
                    .selectableGroup(),
            ) {
                DnsOptions.servers.entries.forEachIndexed { index, (id, server) ->
                    DnsProviderRow(
                        title = stringResource(DNS_LABELS.getValue(id)),
                        subtitle = stringResource(DNS_SUBTITLES.getValue(id)),
                        server = server,
                        iconRes = DNS_ICONS.getValue(id),
                        selected = !state.editingCustom && state.selectedDns == id,
                        onClick = { viewModel.chooseKnown(id) },
                    )
                    if (index < DnsOptions.servers.size - 1) GroupDivider(startInset = 80)
                }
                GroupDivider(startInset = 80)
                DnsProviderRow(
                    title = stringResource(R.string.dns_custom),
                    subtitle = stringResource(R.string.dns_custom_subtitle),
                    server = state.customField.takeIf { it.isNotBlank() },
                    iconRes = R.drawable.ic_globe,
                    selected = state.editingCustom,
                    onClick = viewModel::editCustom,
                )

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
                        GroupDivider(startInset = 20)
                        DetourInputField(
                            value = state.customField,
                            onValueChange = viewModel::setCustomField,
                            label = stringResource(R.string.dns_custom_label),
                            placeholder = stringResource(R.string.dns_placeholder),
                            error = when {
                                state.customInvalid -> stringResource(R.string.dns_invalid_https)
                                state.saveState == DnsSaveState.ERROR -> stringResource(R.string.dns_save_error)
                                else -> null
                            },
                            modifier = Modifier.padding(Spacing.space16),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.space16))
            DnsInfoNotice(
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )

            if (state.editingCustom) {
                Spacer(Modifier.height(Spacing.space16))
                DetourButton(
                    text = if (state.saveState == DnsSaveState.SAVING) {
                        stringResource(R.string.dns_saving)
                    } else {
                        stringResource(R.string.btn_save)
                    },
                    onClick = viewModel::saveCustom,
                    enabled = state.canSaveCustom,
                    height = 58,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
            }

            Spacer(Modifier.height(Spacing.space24))
        }
    }
}

@Composable
private fun DnsProviderRow(
    title: String,
    subtitle: String,
    server: String?,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = detourColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, c.accentBorder, AppShapes.small)
                } else {
                    Modifier
                },
            )
            .detourSelectable(
                selected = selected,
                onClick = { if (!selected) onClick() },
                idleColor = if (selected) c.accentSoft.copy(alpha = 0.48f) else Color.Transparent,
                pressedColor = if (selected) c.accentSoft else c.surfaceSelected,
                pressScale = Motion.PRESS_RADIO,
            )
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(if (selected) c.accentSoft else c.surfaceSoft, AppShapes.small)
                .border(
                    1.dp,
                    if (selected) c.accentBorder.copy(alpha = 0.72f) else c.border,
                    AppShapes.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (selected) c.accent else c.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Spacing.space2),
            )
            if (!server.isNullOrBlank()) {
                Text(
                    text = server,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.space2),
                )
            }
        }
        SelectionMark(
            selected = selected,
            modifier = Modifier.padding(start = Spacing.space8),
        )
    }
}

@Composable
private fun DnsInfoNotice(modifier: Modifier = Modifier) {
    val c = detourColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.accentSoft.copy(alpha = 0.55f), AppShapes.medium)
            .border(1.dp, c.accentBorder.copy(alpha = 0.42f), AppShapes.medium)
            .padding(Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(c.surface, AppShapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "i",
                style = MaterialTheme.typography.titleSmall,
                color = c.accent,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = stringResource(R.string.dns_info),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        )
    }
}
