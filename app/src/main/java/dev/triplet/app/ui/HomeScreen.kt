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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.EffectiveRoutes
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import dev.triplet.app.vpn.resolveEffectiveRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class HomeProtocol { VLESS_DPI, DPI, VLESS, NONE }
private enum class VisualVpnState { IDLE, STARTING, ACTIVE, FAILED }

fun homeProtocol(routes: EffectiveRoutes): HomeProtocol {
    val dpi = routes.dpiPackages.isNotEmpty()
    val vpn = routes.vpnPackages.isNotEmpty()
    return when {
        dpi && vpn -> HomeProtocol.VLESS_DPI
        dpi -> HomeProtocol.DPI
        vpn -> HomeProtocol.VLESS
        else -> HomeProtocol.NONE
    }
}

private fun visualKey(state: VpnState): VisualVpnState = when (state) {
    VpnState.Idle -> VisualVpnState.IDLE
    VpnState.Starting -> VisualVpnState.STARTING
    VpnState.Active -> VisualVpnState.ACTIVE
    is VpnState.Failed -> VisualVpnState.FAILED
}

@Composable
fun HomeScreen(store: RoutesStore, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state by VpnController.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val st = state
    val c = detourColors
    val settings by store.settings.collectAsStateWithLifecycle()
    val persistedRoutes = settings?.routes.orEmpty()
    var routeRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(ctx) {
        val owner = ctx as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) routeRevision++
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val effectiveRoutes by produceState(
        initialValue = EffectiveRoutes(emptySet(), emptySet()),
        key1 = persistedRoutes,
        key2 = routeRevision,
    ) {
        value = if (persistedRoutes.isEmpty()) {
            EffectiveRoutes(emptySet(), emptySet())
        } else {
            withContext(Dispatchers.IO) {
                resolveEffectiveRoutes(ctx.packageManager, persistedRoutes)
            }
        }
    }

    var showStarting by remember { mutableStateOf(false) }
    var lastSettledState by remember {
        mutableStateOf<VpnState>(if (st == VpnState.Starting) VpnState.Idle else st)
    }
    LaunchedEffect(st) {
        if (st == VpnState.Starting) {
            showStarting = false
            delay(Motion.DEFERRED_BUSY_MS)
            if (VpnController.state.value == VpnState.Starting) showStarting = true
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

    val status = statusStyleFor(c, visualState)
    val style = StatusStyle(
        container = animateColorAsState(status.container, tween(Motion.STATE_MS), label = "cardBg").value,
        content = animateColorAsState(status.content, tween(Motion.COLOR_MS), label = "cardFg").value,
        border = animateColorAsState(status.border, tween(Motion.STATE_MS), label = "cardBorder").value,
    )

    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(st, settings?.sessionStartedAt) {
        if (st == VpnState.Active) {
            val started = settings?.sessionStartedAt ?: System.currentTimeMillis()
            while (true) {
                elapsed = ((System.currentTimeMillis() - started) / 1000L).coerceAtLeast(0).toInt()
                delay(1000)
            }
        } else elapsed = 0
    }
    val timerText = remember(elapsed) {
        val h = elapsed / 3600
        val m = (elapsed % 3600) / 60
        val sec = elapsed % 60
        "%02d:%02d:%02d".format(h, m, sec)
    }
    val serverHost = remember(settings?.vlessUri, settings?.warpProfile, settings?.activeVpn) {
        when (settings?.activeVpn ?: VpnProfileKind.VLESS) {
            VpnProfileKind.VLESS ->
                (VlessKeyParser.parse(settings?.vlessUri ?: "") as? ParseResult.Ok)?.profile?.server
            VpnProfileKind.WARP -> settings?.warpProfile?.name
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) VpnController.startNow(ctx)
    }
    val onMainAction: () -> Unit = {
        if (st == VpnState.Active) VpnController.stop(ctx)
        else if (st != VpnState.Starting) VpnController.start(ctx, consentLauncher::launch)
    }

    val activeVpn = settings?.activeVpn ?: VpnProfileKind.VLESS
    val protocolRes = when (homeProtocol(effectiveRoutes)) {
        HomeProtocol.VLESS_DPI -> if (activeVpn == VpnProfileKind.WARP) R.string.protocol_warp_dpi else R.string.protocol_vless_dpi
        HomeProtocol.DPI -> R.string.protocol_dpi
        HomeProtocol.VLESS -> if (activeVpn == VpnProfileKind.WARP) R.string.protocol_warp else R.string.protocol_vless
        HomeProtocol.NONE -> R.string.protocol_none
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = c.background,
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
                MainButton(
                    state = visualState,
                    busy = st == VpnState.Starting,
                    onClick = onMainAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space20),
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

            Spacer(Modifier.weight(1f))
            StatusCard(
                state = visualState,
                style = style,
                timerText = timerText,
                serverHost = serverHost,
                protocol = stringResource(protocolRes),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space16)
                    .widthIn(max = 360.dp),
            )
            Spacer(Modifier.weight(1.35f))
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
    val activeMain = state == VpnState.Active
    val container by animateColorAsState(
        when {
            activeMain -> c.surfaceSelected
            state == VpnState.Starting -> c.surface
            else -> c.accent
        },
        tween(Motion.STATE_MS), label = "btnBg",
    )
    val content by animateColorAsState(
        when {
            activeMain -> c.accent
            state == VpnState.Starting -> c.accent
            else -> c.onAccent
        },
        tween(Motion.COLOR_MS), label = "btnFg",
    )
    val borderColor by animateColorAsState(
        when {
            activeMain -> c.accentBorder
            state == VpnState.Starting -> c.accentBorder
            else -> Color.Transparent
        },
        tween(Motion.STATE_MS), label = "btnBorder",
    )
    val text = when (state) {
        VpnState.Active -> stringResource(R.string.btn_disconnect)
        VpnState.Starting -> stringResource(R.string.btn_connecting)
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
private fun StatusCard(
    state: VpnState,
    style: StatusStyle,
    timerText: String,
    serverHost: String?,
    protocol: String,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val key = visualKey(state)
    Column(
        modifier
            .clip(AppShapes.medium)
            .background(style.container)
            .border(1.dp, style.border, AppShapes.medium)
            .padding(Spacing.space20),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = key,
            transitionSpec = {
                fadeIn(tween(Motion.CONTENT_IN_MS)) togetherWith
                    fadeOut(tween(Motion.CONTENT_OUT_MS))
            },
            label = "statusTitle",
        ) { target ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val titleRes = when (target) {
                    VisualVpnState.ACTIVE -> R.string.status_active
                    VisualVpnState.STARTING -> R.string.status_starting
                    VisualVpnState.FAILED -> R.string.status_failed
                    VisualVpnState.IDLE -> R.string.status_idle
                }
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = style.content,
                )
                if (target == VisualVpnState.STARTING) {
                    Spacer(Modifier.size(10.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = style.content,
                    )
                }
            }
        }

        AnimatedContent(
            targetState = key,
            transitionSpec = {
                fadeIn(tween(Motion.CONTENT_IN_MS)) togetherWith
                    fadeOut(tween(Motion.CONTENT_OUT_MS))
            },
            label = "statusSubtitle",
        ) { target ->
            when (target) {
                VisualVpnState.FAILED -> Text(
                    (state as? VpnState.Failed)?.reason ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Spacing.space4),
                )
                VisualVpnState.IDLE -> Text(
                    stringResource(R.string.state_sub_idle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Spacing.space4),
                )
                VisualVpnState.STARTING -> Text(
                    stringResource(R.string.btn_connecting),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    ),
                    color = style.content,
                    modifier = Modifier.padding(top = Spacing.space4),
                )
                VisualVpnState.ACTIVE -> Text(
                    timerText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                    color = style.content,
                    modifier = Modifier.padding(top = Spacing.space4),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        Spacer(Modifier.height(10.dp))

        InfoRow(stringResource(R.string.row_protocol), protocol)
        InfoRow(
            stringResource(R.string.row_server),
            serverHost ?: stringResource(R.string.server_missing),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = detourColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
    }
}
