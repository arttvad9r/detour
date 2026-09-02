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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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

internal fun homeUsesSplitLayout(windowSizeClass: WindowSizeClass): Boolean =
    windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

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
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space12)
                        .widthIn(max = 520.dp)
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
                    .padding(start = Spacing.space16, end = Spacing.space8, top = Spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetourBrandWordmark(Modifier.weight(1f))
                DetourIconButton(onClick = onOpenSettings) {
                    Icon(
                        painter = painterResource(R.drawable.ic_gear),
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = c.textPrimary,
                        modifier = Modifier.size(24.dp),
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
                    profileName = uiState.profileName,
                    activeVpn = uiState.activeVpn,
                    serverHost = uiState.serverHost,
                    endpointCount = uiState.endpointCount,
                    routedCount = uiState.routedCount,
                    homeProtocol = uiState.protocol,
                    protocol = stringResource(protocolRes),
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
    profileName: String?,
    activeVpn: VpnProfileKind,
    serverHost: String?,
    endpointCount: Int,
    routedCount: Int,
    homeProtocol: HomeProtocol,
    protocol: String,
    dns: String,
    onOpenProfiles: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenRoutes: () -> Unit,
) {
    val contentModifier = Modifier
        .padding(horizontal = Spacing.space16, vertical = Spacing.space8)
        .widthIn(max = if (splitLayout) 1040.dp else 520.dp)
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
                homeProtocol = homeProtocol,
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
            modifier = contentModifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ConnectionHero(
                state = state,
                statusContent = statusContent,
                sessionStartedAt = sessionStartedAt,
                homeProtocol = homeProtocol,
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
            Spacer(Modifier.height(Spacing.space8))
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

    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = modifier.heightIn(min = 58.dp),
        shape = AppShapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        border = if (borderColor != Color.Transparent) {
            androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        } else null,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = content,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_power),
                contentDescription = null,
                modifier = Modifier.size(25.dp),
            )
        }
        Spacer(Modifier.width(Spacing.space12))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ConnectionHero(
    state: VpnState,
    statusContent: Color,
    sessionStartedAt: Long?,
    homeProtocol: HomeProtocol,
    protocol: String,
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
            ProtocolBadge(protocol = protocol, homeProtocol = homeProtocol)
            Spacer(Modifier.height(Spacing.space12))
            ConnectionRouteDiagram(homeProtocol)
            Spacer(Modifier.height(Spacing.space12))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(statusContent.copy(alpha = 0.12f), CircleShape)
                        .border(1.5.dp, statusContent.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (target == VisualVpnState.STARTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = statusContent,
                        )
                    } else if (target == VisualVpnState.ACTIVE) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = statusContent,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(statusContent, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.space12))
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
                    modifier = Modifier.padding(top = Spacing.space4),
                )
                VisualVpnState.STARTING -> Unit
                VisualVpnState.ACTIVE -> SessionTimer(
                    sessionStartedAt = sessionStartedAt,
                    color = c.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun ProtocolBadge(protocol: String, homeProtocol: HomeProtocol) {
    val c = detourColors
    val protocolIcon = when (homeProtocol) {
        HomeProtocol.VLESS_DPI, HomeProtocol.DPI -> R.drawable.ic_dpi
        HomeProtocol.VLESS -> R.drawable.ic_lock
        HomeProtocol.NONE -> R.drawable.ic_globe
    }
    Row(
        modifier = Modifier
            .background(c.accentSoft, PillShape)
            .border(1.dp, c.accentBorder.copy(alpha = 0.45f), PillShape)
            .padding(horizontal = Spacing.space20, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(protocolIcon),
            contentDescription = null,
            tint = c.accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(Spacing.space8))
        Text(
            text = protocol,
            style = MaterialTheme.typography.titleMedium,
            color = c.accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ConnectionRouteDiagram(homeProtocol: HomeProtocol) {
    val c = detourColors
    val directSelected = homeProtocol == HomeProtocol.NONE
    val dpiSelected = homeProtocol == HomeProtocol.DPI || homeProtocol == HomeProtocol.VLESS_DPI
    val vpnSelected = homeProtocol == HomeProtocol.VLESS

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 450.dp)
            .height(214.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val midY = size.height / 2f
            val topY = 42.dp.toPx()
            val bottomY = size.height - 42.dp.toPx()
            val startX = 58.dp.toPx()
            val endX = size.width - 58.dp.toPx()
            val sideLeft = 112.dp.toPx()
            val sideRight = size.width - 112.dp.toPx()
            val chipLeft = size.width / 2f - 68.dp.toPx()
            val chipRight = size.width / 2f + 68.dp.toPx()
            val neutral = c.textMuted.copy(alpha = 0.42f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()))

            fun routePath(y: Float): Path = Path().apply {
                moveTo(startX, midY)
                cubicTo(sideLeft, midY, sideLeft, y, chipLeft, y)
                lineTo(chipRight, y)
                cubicTo(sideRight, y, sideRight, midY, endX, midY)
            }

            drawPath(
                routePath(topY),
                color = if (directSelected) c.accent else neutral,
                style = Stroke(
                    width = if (directSelected) 3.dp.toPx() else 2.dp.toPx(),
                    pathEffect = if (directSelected) null else dash,
                ),
            )
            drawLine(
                color = if (dpiSelected) c.accent else neutral,
                start = androidx.compose.ui.geometry.Offset(startX, midY),
                end = androidx.compose.ui.geometry.Offset(endX, midY),
                strokeWidth = if (dpiSelected) 3.dp.toPx() else 2.dp.toPx(),
            )
            drawPath(
                routePath(bottomY),
                color = if (vpnSelected) c.accent else neutral,
                style = Stroke(
                    width = if (vpnSelected) 3.dp.toPx() else 2.dp.toPx(),
                    pathEffect = if (vpnSelected) null else dash,
                ),
            )

            listOf(startX, endX).forEach { x ->
                drawCircle(c.accentSoft, radius = 14.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, midY))
                drawCircle(c.accent, radius = 9.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, midY))
                drawCircle(c.surface, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, midY))
            }
        }

        RouteEndpoint(
            iconRes = R.drawable.ic_routes,
            label = stringResource(R.string.home_device),
            modifier = Modifier.align(Alignment.CenterStart),
        )
        RouteEndpoint(
            iconRes = R.drawable.ic_globe,
            label = stringResource(R.string.home_internet),
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        RouteModeChip(
            text = stringResource(R.string.route_direct),
            iconRes = R.drawable.ic_globe,
            selected = directSelected,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        RouteModeChip(
            text = stringResource(R.string.route_dpi),
            iconRes = R.drawable.ic_dpi,
            selected = dpiSelected,
            modifier = Modifier.align(Alignment.Center),
        )
        RouteModeChip(
            text = stringResource(R.string.route_vpn),
            iconRes = R.drawable.ic_lock,
            selected = vpnSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun RouteModeChip(
    text: String,
    iconRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    Row(
        modifier = modifier
            .widthIn(min = 124.dp)
            .background(if (selected) c.surface else c.surfaceSoft, AppShapes.small)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) c.accent else c.border,
                AppShapes.small,
            )
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (selected) c.accent else c.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.space8))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) c.accent else c.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun RouteEndpoint(
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    Column(
        modifier = modifier
            .width(76.dp)
            .background(c.surface, AppShapes.small)
            .border(1.dp, c.border, AppShapes.small)
            .padding(horizontal = Spacing.space8, vertical = Spacing.space12),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = c.accent,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.space8),
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
        Column(Modifier.padding(vertical = Spacing.space4)) {
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
                    onClick = onOpenProfiles,
                )
            } else {
                HomeInfoRow(
                    iconRes = R.drawable.ic_server,
                    label = stringResource(R.string.row_server),
                    value = serverHost ?: stringResource(R.string.server_missing),
                    onClick = onOpenProfiles,
                )
            }
            DetailsDivider()
            HomeInfoRow(
                iconRes = R.drawable.ic_detour_mark,
                label = stringResource(R.string.row_dns),
                value = dns,
                onClick = onOpenDns,
            )
            DetailsDivider()
            HomeInfoRow(
                iconRes = R.drawable.ic_apps,
                label = stringResource(R.string.home_apps_count, routedCount),
                value = stringResource(R.string.home_apps_selected, routedCount),
                onClick = onOpenRoutes,
            )
        }
    }
}

@Composable
private fun DetailsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 78.dp, end = Spacing.space16)
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
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
        ),
        color = color,
        modifier = Modifier.padding(top = Spacing.space4),
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
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .detourClickable(
                onClick = onClick,
                role = androidx.compose.ui.semantics.Role.Button,
                pressedColor = c.surfaceSelected.copy(alpha = 0.38f),
                pressScale = Motion.PRESS_ROW,
            )
    } else {
        modifier.fillMaxWidth().heightIn(min = 70.dp)
    }
    Row(
        rowModifier.padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeIconTile(iconRes)
        Column(
            Modifier
                .padding(start = Spacing.space16)
                .weight(1f),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.space2),
                )
            }
        }
        if (onClick != null) Chevron()
    }
}

@Composable
private fun HomeIconTile(iconRes: Int) {
    val c = detourColors
    Box(
        Modifier
            .size(48.dp)
            .background(c.accentSoft, AppShapes.extraSmall)
            .border(1.dp, c.accentBorder.copy(alpha = 0.35f), AppShapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = c.accent,
            modifier = Modifier.size(24.dp),
        )
    }
}
