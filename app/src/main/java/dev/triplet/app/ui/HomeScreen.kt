package dev.triplet.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import dev.triplet.app.R
import dev.triplet.app.core.DnsOptions
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOME_PROFILE_ROW_TEST_TAG = "home_profile_row"

private enum class VisualVpnState { IDLE, STARTING, ACTIVE, FAILED }

private fun visualKey(state: VpnState): VisualVpnState = when (state) {
    VpnState.Idle -> VisualVpnState.IDLE
    VpnState.Starting -> VisualVpnState.STARTING
    VpnState.Active -> VisualVpnState.ACTIVE
    is VpnState.Failed -> VisualVpnState.FAILED
}

internal fun formatSessionElapsed(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val sec = safe % 60
    return "%02d:%02d:%02d".format(h, m, sec)
}

internal fun homeUsesSplitLayout(windowSizeClass: WindowSizeClass): Boolean =
    windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenDns: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val st = uiState.vpnState
    val c = detourColors
    val splitLayout = homeUsesSplitLayout(currentWindowAdaptiveInfoV2().windowSizeClass)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshRoutes()
    }

    var showStarting by remember { mutableStateOf(false) }
    var lastSettledState by remember {
        mutableStateOf<VpnState>(if (st == VpnState.Starting) VpnState.Idle else st)
    }
    LaunchedEffect(st) {
        if (st == VpnState.Starting) {
            showStarting = false
            delay(Motion.DEFERRED_BUSY_MS)
            if (viewModel.uiState.value.vpnState == VpnState.Starting) showStarting = true
        } else {
            showStarting = false
            lastSettledState = st
        }
    }
    val visualState = if (st == VpnState.Starting && !showStarting) lastSettledState else st

    var previousEngineState by remember { mutableStateOf<VpnState?>(null) }
    LaunchedEffect(st) {
        val previous = previousEngineState
        if (previous != null) {
            when {
                st == VpnState.Active && previous != VpnState.Active ->
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                st is VpnState.Failed && previous !is VpnState.Failed ->
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
            }
        }
        previousEngineState = st
    }

    val targetStatus = statusStyleFor(c, visualState)
    val statusContent = animateColorAsState(
        targetStatus.content,
        tween(Motion.COLOR_MS),
        label = "statusContent",
    ).value

    val snackbarHostState = remember { SnackbarHostState() }
    val notificationPermissionTracker = rememberManualNotificationPermissionTracker(
        vpnState = st,
        snackbarHostState = snackbarHostState,
    )
    val scope = rememberCoroutineScope()
    val permissionNotGrantedMessage = stringResource(R.string.err_vpn_permission)
    val consentLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            VpnController.startNow(ctx)
        } else {
            notificationPermissionTracker.cancel()
            scope.launch {
                snackbarHostState.showSnackbar(permissionNotGrantedMessage)
            }
        }
    }
    val canCancelStarting = st == VpnState.Starting && showStarting
    val onMainAction: () -> Unit = {
        when {
            st == VpnState.Active -> {
                notificationPermissionTracker.cancel()
                VpnController.stop(ctx)
            }
            canCancelStarting -> {
                notificationPermissionTracker.cancel()
                VpnController.stop(ctx)
            }
            st != VpnState.Starting -> {
                notificationPermissionTracker.begin()
                VpnController.start(ctx, consentLauncher::launch)
            }
        }
    }

    val protocolRes = when (uiState.protocol) {
        HomeProtocol.VLESS_DPI -> if (uiState.activeVpn == VpnProfileKind.WARP) R.string.protocol_warp_dpi else R.string.protocol_vless_dpi
        HomeProtocol.DPI -> R.string.protocol_dpi
        HomeProtocol.VLESS -> if (uiState.activeVpn == VpnProfileKind.WARP) R.string.protocol_warp else R.string.protocol_vless
        HomeProtocol.NONE -> R.string.protocol_none
    }
    val dnsValue = when (uiState.dnsId) {
        "google" -> stringResource(R.string.dns_google)
        "cloudflare" -> stringResource(R.string.dns_cloudflare)
        "adguard" -> stringResource(R.string.dns_adguard)
        DnsOptions.CUSTOM -> uiState.dnsCustom.ifBlank { stringResource(R.string.server_missing) }
        else -> stringResource(R.string.server_missing)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
                MainButton(
                    state = visualState,
                    busy = st == VpnState.Starting && !showStarting,
                    onClick = onMainAction,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space20)
                        .widthIn(max = 480.dp)
                        .fillMaxWidth(),
                )
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.space16, end = Spacing.space8, top = Spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                DetourIconButton(onClick = onOpenSettings) {
                    Icon(
                        painter = painterResource(R.drawable.ic_gear),
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = c.textSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                HomeConnectionContent(
                    splitLayout = splitLayout,
                    state = visualState,
                    statusContent = statusContent,
                    sessionStartedAt = uiState.sessionStartedAt,
                    profileName = uiState.profileName,
                    activeVpn = uiState.activeVpn,
                    serverHost = uiState.serverHost,
                    endpointCount = uiState.endpointCount,
                    protocol = stringResource(protocolRes),
                    dns = dnsValue,
                    onOpenProfiles = onOpenProfiles,
                    onOpenDns = onOpenDns,
                )
            }
        }
    }
}

@Composable
private fun HomeConnectionContent(
    splitLayout: Boolean,
    state: VpnState,
    statusContent: Color,
    sessionStartedAt: Long?,
    profileName: String?,
    activeVpn: VpnProfileKind,
    serverHost: String?,
    endpointCount: Int,
    protocol: String,
    dns: String,
    onOpenProfiles: () -> Unit,
    onOpenDns: () -> Unit,
) {
    val contentModifier = Modifier
        .padding(horizontal = Spacing.space16, vertical = Spacing.space24)
        .widthIn(max = if (splitLayout) 960.dp else 480.dp)
        .fillMaxWidth()

    if (splitLayout) {
        Row(
            modifier = contentModifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space32),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConnectionHero(
                state = state,
                statusContent = statusContent,
                sessionStartedAt = sessionStartedAt,
                modifier = Modifier.weight(1f),
            )
            ConnectionDetails(
                profileName = profileName,
                activeVpn = activeVpn,
                serverHost = serverHost,
                endpointCount = endpointCount,
                protocol = protocol,
                dns = dns,
                onOpenProfiles = onOpenProfiles,
                onOpenDns = onOpenDns,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(
            modifier = contentModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ConnectionHero(
                state = state,
                statusContent = statusContent,
                sessionStartedAt = sessionStartedAt,
            )
            Spacer(Modifier.height(Spacing.space24))
            ConnectionDetails(
                profileName = profileName,
                activeVpn = activeVpn,
                serverHost = serverHost,
                endpointCount = endpointCount,
                protocol = protocol,
                dns = dns,
                onOpenProfiles = onOpenProfiles,
                onOpenDns = onOpenDns,
            )
        }
    }
}

@Composable
private fun MainButton(
    state: VpnState,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val container by animateColorAsState(
        if (state == VpnState.Starting) c.surface else c.accent,
        tween(Motion.STATE_MS),
        label = "btnBg",
    )
    val content by animateColorAsState(
        if (state == VpnState.Starting) c.accent else c.onAccent,
        tween(Motion.COLOR_MS),
        label = "btnFg",
    )
    val borderColor by animateColorAsState(
        if (state == VpnState.Starting) c.accentBorder else Color.Transparent,
        tween(Motion.STATE_MS),
        label = "btnBorder",
    )
    val text = when (state) {
        VpnState.Active -> stringResource(R.string.btn_disconnect)
        VpnState.Starting -> stringResource(R.string.btn_cancel_connecting)
        else -> stringResource(R.string.btn_connect)
    }

    DetourButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = !busy,
        container = container,
        contentColor = content,
        disabledContainer = container,
        disabledContent = content,
        borderColor = borderColor,
    )
}

@Composable
private fun ConnectionHero(
    state: VpnState,
    statusContent: Color,
    sessionStartedAt: Long?,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val key = visualKey(state)

    AnimatedContent(
        targetState = key,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            fadeIn(tween(Motion.CONTENT_IN_MS)) togetherWith
                fadeOut(tween(Motion.CONTENT_OUT_MS))
        },
        label = "connectionHero",
    ) { target ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(statusContent, CircleShape),
                )
                Spacer(Modifier.size(Spacing.space8))
                val titleRes = when (target) {
                    VisualVpnState.ACTIVE -> R.string.status_active
                    VisualVpnState.STARTING -> R.string.status_starting
                    VisualVpnState.FAILED -> R.string.status_failed
                    VisualVpnState.IDLE -> R.string.status_idle
                }
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.textPrimary,
                )
                if (target == VisualVpnState.STARTING) {
                    Spacer(Modifier.size(Spacing.space8))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.5.dp,
                        color = statusContent,
                    )
                }
            }

            when (target) {
                VisualVpnState.FAILED -> Text(
                    (state as? VpnState.Failed)?.reason.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = Spacing.space8)
                        .widthIn(max = 360.dp),
                )
                VisualVpnState.IDLE -> Text(
                    stringResource(R.string.state_sub_idle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Spacing.space8),
                )
                VisualVpnState.STARTING -> Unit
                VisualVpnState.ACTIVE -> SessionTimer(
                    sessionStartedAt = sessionStartedAt,
                    color = statusContent,
                )
            }
        }
    }
}

@Composable
private fun ConnectionDetails(
    profileName: String?,
    activeVpn: VpnProfileKind,
    serverHost: String?,
    endpointCount: Int,
    protocol: String,
    dns: String,
    onOpenProfiles: () -> Unit,
    onOpenDns: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetourCard(modifier) {
        Column(
            Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        ) {
            InfoRow(
                stringResource(R.string.row_profile),
                profileName ?: stringResource(R.string.server_missing),
                modifier = Modifier.testTag(HOME_PROFILE_ROW_TEST_TAG),
                onClick = onOpenProfiles,
            )
            DetailsDivider()
            if (activeVpn == VpnProfileKind.WARP) {
                InfoRow(
                    stringResource(R.string.row_endpoints),
                    endpointCount.takeIf { it > 0 }?.toString() ?: stringResource(R.string.server_missing),
                )
            } else {
                InfoRow(
                    stringResource(R.string.row_server),
                    serverHost ?: stringResource(R.string.server_missing),
                )
            }
            DetailsDivider()
            InfoRow(stringResource(R.string.row_protocol), protocol)
            DetailsDivider()
            InfoRow(
                stringResource(R.string.row_dns),
                dns,
                onClick = onOpenDns,
            )
        }
    }
}

@Composable
private fun DetailsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.space8)
            .height(1.dp)
            .background(detourColors.divider),
    )
}

@Composable
private fun SessionTimer(sessionStartedAt: Long?, color: Color) {
    var elapsed by remember(sessionStartedAt) { mutableIntStateOf(0) }
    LaunchedEffect(sessionStartedAt) {
        val started = sessionStartedAt ?: System.currentTimeMillis()
        while (true) {
            elapsed = ((System.currentTimeMillis() - started) / 1000L).coerceAtLeast(0).toInt()
            delay(1000)
        }
    }
    Text(
        formatSessionElapsed(elapsed),
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            fontFeatureSettings = "tnum",
        ),
        color = color,
        modifier = Modifier.padding(top = Spacing.space8),
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = detourColors
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .detourClickable(
                onClick = onClick,
                role = androidx.compose.ui.semantics.Role.Button,
                pressedColor = c.surfaceSelected.copy(alpha = 0.38f),
                pressScale = Motion.PRESS_ROW,
            )
    } else {
        modifier.fillMaxWidth()
    }
    Row(
        rowModifier.padding(vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            modifier = Modifier.widthIn(min = 88.dp),
        )
        AnimatedContent(
            targetState = value,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                fadeIn(tween(Motion.CONTENT_IN_MS)) togetherWith
                    fadeOut(tween(Motion.CONTENT_OUT_MS))
            },
            label = "infoValue",
        ) { shown ->
            Text(
                shown,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) Chevron()
    }
}
