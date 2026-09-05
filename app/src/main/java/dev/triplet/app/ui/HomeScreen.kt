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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
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

internal fun formatTrafficBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe < 1_000L -> "$safe B"
        safe < 1_000_000L -> formatScaledTraffic(safe, 1_000L, "KB")
        safe < 1_000_000_000L -> formatScaledTraffic(safe, 1_000_000L, "MB")
        safe < 1_000_000_000_000L -> formatScaledTraffic(safe, 1_000_000_000L, "GB")
        else -> formatScaledTraffic(safe, 1_000_000_000_000L, "TB")
    }
}

internal fun formatTrafficRate(bytesPerSecond: Long): String =
    "${formatTrafficBytes(bytesPerSecond)}/s"

private fun formatScaledTraffic(value: Long, divisor: Long, suffix: String): String {
    val whole = value / divisor
    val tenth = ((value % divisor) * 10L / divisor).toInt()
    return if (whole >= 100L || tenth == 0) "$whole $suffix" else "$whole.$tenth $suffix"
}

internal fun homeUsesSplitLayout(windowSizeClass: WindowSizeClass): Boolean =
    windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

internal fun homeProtocolLabelRes(protocol: HomeProtocol, activeVpn: VpnProfileKind): Int = when (protocol) {
    HomeProtocol.VLESS_DPI -> when (activeVpn) {
        VpnProfileKind.VLESS, VpnProfileKind.SUBSCRIPTION -> R.string.protocol_vless_dpi
        VpnProfileKind.WARP -> R.string.protocol_warp_dpi
    }
    HomeProtocol.DPI -> R.string.protocol_dpi
    HomeProtocol.VLESS -> when (activeVpn) {
        VpnProfileKind.VLESS, VpnProfileKind.SUBSCRIPTION -> R.string.protocol_vless
        VpnProfileKind.WARP -> R.string.protocol_warp
    }
    HomeProtocol.NONE -> R.string.protocol_none
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenRoutes: () -> Unit,
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
    val statusContent by animateColorAsState(
        targetStatus.content,
        tween(Motion.COLOR_MS),
        label = "statusContent",
    )

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
            scope.launch { snackbarHostState.showSnackbar(permissionNotGrantedMessage) }
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

    val protocol = stringResource(homeProtocolLabelRes(uiState.protocol, uiState.activeVpn))
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
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space8)
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
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.space16, end = Spacing.space8, top = Spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetourBrandWordmark(Modifier.weight(1f))
                DetourIconButton(onClick = onOpenSettings) {
                    Icon(
                        painter = painterResource(R.drawable.ic_gear),
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = c.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                HomeConnectionContent(
                    splitLayout = splitLayout,
                    state = visualState,
                    statusContent = statusContent,
                    sessionStartedAt = uiState.sessionStartedAt,
                    traffic = uiState.traffic,
                    profileName = uiState.profileName,
                    activeVpn = uiState.activeVpn,
                    serverHost = uiState.serverHost,
                    endpointCount = uiState.endpointCount,
                    routedCount = uiState.routedCount,
                    protocol = protocol,
                    dns = dnsValue,
                    onOpenProfiles = onOpenProfiles,
                    onOpenDns = onOpenDns,
                    onOpenRoutes = onOpenRoutes,
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
    traffic: HomeTrafficStats,
    profileName: String?,
    activeVpn: VpnProfileKind,
    serverHost: String?,
    endpointCount: Int,
    routedCount: Int,
    protocol: String,
    dns: String,
    onOpenProfiles: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenRoutes: () -> Unit,
) {
    val contentModifier = Modifier
        .padding(horizontal = Spacing.space16, vertical = Spacing.space4)
        .widthIn(max = if (splitLayout) 900.dp else 480.dp)
        .fillMaxWidth()
        .then(if (!splitLayout) Modifier.verticalScroll(rememberScrollState()) else Modifier)

    if (splitLayout) {
        Row(
            modifier = contentModifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space24),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConnectionHero(
                state = state,
                statusContent = statusContent,
                sessionStartedAt = sessionStartedAt,
                traffic = traffic,
                protocol = protocol,
                modifier = Modifier.weight(1f),
            )
            ConnectionDetails(
                profileName = profileName,
                activeVpn = activeVpn,
                serverHost = serverHost,
                endpointCount = endpointCount,
                routedCount = routedCount,
                dns = dns,
                onOpenProfiles = onOpenProfiles,
                onOpenDns = onOpenDns,
                onOpenRoutes = onOpenRoutes,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(
            modifier = contentModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ConnectionHero(
                state = state,
                statusContent = statusContent,
                sessionStartedAt = sessionStartedAt,
                traffic = traffic,
                protocol = protocol,
            )
            Spacer(Modifier.height(Spacing.space12))
            ConnectionDetails(
                profileName = profileName,
                activeVpn = activeVpn,
                serverHost = serverHost,
                endpointCount = endpointCount,
                routedCount = routedCount,
                dns = dns,
                onOpenProfiles = onOpenProfiles,
                onOpenDns = onOpenDns,
                onOpenRoutes = onOpenRoutes,
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
        height = 52,
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
    traffic: HomeTrafficStats,
    protocol: String,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val key = visualKey(state)
    val protocolNone = stringResource(R.string.protocol_none)
    val routeDescription = protocol.takeUnless { it == protocolNone }.orEmpty()

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
            MinimalRouteLine(
                color = statusContent,
                active = target == VisualVpnState.ACTIVE || target == VisualVpnState.STARTING,
            )
            Spacer(Modifier.height(Spacing.space12))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (target == VisualVpnState.STARTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 1.5.dp,
                        color = statusContent,
                    )
                    Spacer(Modifier.width(Spacing.space8))
                }
                val titleRes = when (target) {
                    VisualVpnState.ACTIVE -> R.string.status_active
                    VisualVpnState.STARTING -> R.string.status_starting
                    VisualVpnState.FAILED -> R.string.status_failed
                    VisualVpnState.IDLE -> R.string.status_idle
                }
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (target == VisualVpnState.FAILED) c.error else c.textPrimary,
                )
            }

            when (target) {
                VisualVpnState.FAILED -> Text(
                    (state as? VpnState.Failed)?.reason.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = Spacing.space4)
                        .widthIn(max = 340.dp),
                )
                VisualVpnState.IDLE -> Text(
                    stringResource(R.string.state_sub_idle),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Spacing.space4),
                )
                VisualVpnState.STARTING,
                VisualVpnState.ACTIVE,
                -> {
                    if (target == VisualVpnState.ACTIVE) {
                        SessionTimer(
                            sessionStartedAt = sessionStartedAt,
                            color = c.textPrimary,
                        )
                        TrafficSummary(traffic)
                    }
                    if (routeDescription.isNotBlank()) {
                        Text(
                            routeDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusContent,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = Spacing.space4)
                                .widthIn(max = 360.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalRouteLine(
    color: Color,
    active: Boolean,
) {
    val c = detourColors
    val lineColor = if (active) color else c.border
    Row(
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteDot(
            label = stringResource(R.string.home_device),
            color = lineColor,
            active = active,
        )
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.space8)
                .height(2.dp)
                .background(lineColor.copy(alpha = if (active) 0.72f else 0.9f), PillShape),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(if (it == 1) 6.dp else 4.dp)
                        .background(lineColor.copy(alpha = if (active) 0.8f else 0.55f), CircleShape),
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.space8)
                .height(2.dp)
                .background(lineColor.copy(alpha = if (active) 0.72f else 0.9f), PillShape),
        )
        RouteDot(
            label = stringResource(R.string.home_internet),
            color = lineColor,
            active = active,
        )
    }
}

@Composable
private fun RouteDot(
    label: String,
    color: Color,
    active: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(84.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(color.copy(alpha = if (active) 0.12f else 0.08f), CircleShape)
                .border(1.5.dp, color.copy(alpha = if (active) 0.72f else 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(color, CircleShape),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = detourColors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.space4),
        )
    }
}

@Composable
private fun ConnectionDetails(
    profileName: String?,
    activeVpn: VpnProfileKind,
    serverHost: String?,
    endpointCount: Int,
    routedCount: Int,
    dns: String,
    onOpenProfiles: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenRoutes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetourCard(modifier) {
        HomeInfoRow(
            iconRes = R.drawable.ic_profile,
            label = stringResource(R.string.row_profile),
            value = profileName ?: stringResource(R.string.server_missing),
            modifier = Modifier.testTag(HOME_PROFILE_ROW_TEST_TAG),
            onClick = onOpenProfiles,
        )
        DetailsDivider()
        if (activeVpn == VpnProfileKind.WARP) {
            HomeInfoRow(
                iconRes = R.drawable.ic_server,
                label = stringResource(R.string.row_endpoints),
                value = endpointCount.takeIf { it > 0 }?.toString() ?: stringResource(R.string.server_missing),
            )
        } else {
            HomeInfoRow(
                iconRes = R.drawable.ic_server,
                label = stringResource(R.string.row_server),
                value = serverHost ?: stringResource(R.string.server_missing),
            )
        }
        DetailsDivider()
        HomeInfoRow(
            iconRes = R.drawable.ic_globe,
            label = stringResource(R.string.row_dns),
            value = dns,
            onClick = onOpenDns,
        )
        DetailsDivider()
        HomeInfoRow(
            iconRes = R.drawable.ic_apps,
            label = stringResource(R.string.nav_routes),
            value = stringResource(R.string.nav_routes_sub, routedCount),
            onClick = onOpenRoutes,
        )
    }
}

@Composable
private fun DetailsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = NavigationRowDividerInset.dp, end = NavigationRowHorizontalPadding)
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
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
        ),
        color = color,
        modifier = Modifier.padding(top = Spacing.space2),
    )
}

@Composable
private fun TrafficSummary(traffic: HomeTrafficStats) {
    val c = detourColors
    Row(
        modifier = Modifier.padding(top = Spacing.space8),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "↓ ${formatTrafficRate(traffic.downloadBytesPerSecond)}",
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = c.textPrimary,
        )
        Text(
            text = "↑ ${formatTrafficRate(traffic.uploadBytesPerSecond)}",
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = c.textPrimary,
        )
    }
    Text(
        text = stringResource(R.string.home_traffic_session, formatTrafficBytes(traffic.totalBytes)),
        style = MaterialTheme.typography.bodySmall,
        color = c.textSecondary,
        modifier = Modifier.padding(top = Spacing.space2),
    )
}

@Composable
private fun HomeInfoRow(
    iconRes: Int,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = detourColors
    val baseModifier = modifier
        .fillMaxWidth()
        .heightIn(min = NavigationRowMinHeight)
    val rowModifier = if (onClick != null) {
        baseModifier.detourClickable(
            onClick = onClick,
            role = androidx.compose.ui.semantics.Role.Button,
            pressedColor = c.surfaceSelected.copy(alpha = 0.38f),
            pressScale = Motion.PRESS_ROW,
        )
    } else {
        baseModifier
    }

    Row(
        rowModifier.padding(
            horizontal = NavigationRowHorizontalPadding,
            vertical = NavigationRowVerticalPadding,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(NavigationRowLeadingTileSize)
                .background(c.accentSoft, AppShapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(NavigationRowLeadingIconSize),
            )
        }
        Column(
            Modifier
                .padding(start = NavigationRowContentGap)
                .weight(1f),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    fadeIn(tween(Motion.CONTENT_IN_MS)) togetherWith
                        fadeOut(tween(Motion.CONTENT_OUT_MS))
                },
                label = "homeInfoValue",
            ) { shown ->
                Text(
                    shown,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = NavigationRowSubtitleGap),
                )
            }
        }
        if (onClick != null) Chevron()
    }
}
