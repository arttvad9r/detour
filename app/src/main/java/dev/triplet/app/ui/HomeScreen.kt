package dev.triplet.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.delay

enum class HomeProtocol { VLESS_DPI, DPI, VLESS, NONE }

fun homeProtocol(routes: Map<String, AppRoute>): HomeProtocol {
    val dpi = routes.values.any { it == AppRoute.DPI }
    val vless = routes.values.any { it == AppRoute.VPN }
    return when {
        dpi && vless -> HomeProtocol.VLESS_DPI
        dpi -> HomeProtocol.DPI
        vless -> HomeProtocol.VLESS
        else -> HomeProtocol.NONE
    }
}

@Composable
fun HomeScreen(store: RoutesStore, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state by VpnController.state.collectAsState()
    val ctx = LocalContext.current
    val st = state
    val c = detourColors
    val theme = LocalDetourTheme.current

    val settings by store.settings.collectAsState()
    val status = theme.statusFor(st)
    val style = StatusStyle(
        container = animateColorAsState(status.container, tween(110), label = "cardBg").value,
        content = animateColorAsState(status.content, tween(110), label = "cardFg").value,
        border = animateColorAsState(status.border, tween(110), label = "cardBorder").value,
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
        val h = elapsed / 3600; val m = (elapsed % 3600) / 60; val sec = elapsed % 60
        "%02d:%02d:%02d".format(h, m, sec)
    }
    val serverHost = remember(settings?.vlessUri) {
        (VlessKeyParser.parse(settings?.vlessUri ?: "") as? ParseResult.Ok)?.profile?.server
    }

    val consentLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) VpnController.startNow(ctx)
    }
    val onMainAction: () -> Unit = {
        if (st == VpnState.Active) VpnController.stop(ctx)
        else VpnController.start(ctx, consentLauncher::launch)
    }

    Box(modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
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
                IconButton(onClick = onOpenSettings) {
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
                state = st,
                style = style,
                timerText = timerText,
                serverHost = serverHost,
                protocol = stringResource(
                    when (homeProtocol(settings?.routes ?: emptyMap())) {
                        HomeProtocol.VLESS_DPI -> R.string.protocol_vless_dpi
                        HomeProtocol.DPI -> R.string.protocol_dpi
                        HomeProtocol.VLESS -> R.string.protocol_vless
                        HomeProtocol.NONE -> R.string.protocol_none
                    },
                ),
                onRetry = onMainAction,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space16)
                    .widthIn(max = 360.dp),
            )
            Spacer(Modifier.weight(1.35f))

            Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
                MainButton(
                    state = st,
                    onClick = onMainAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space20),
                )
            }
        }
    }
}

@Composable
private fun MainButton(
    state: VpnState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val activeMain = state == VpnState.Active
    val container by animateColorAsState(
        when {
            activeMain -> c.activeSoft
            state == VpnState.Starting -> c.accentSoft
            else -> c.accent
        },
        tween(110), label = "btnBg",
    )
    val content by animateColorAsState(
        when {
            activeMain -> c.activeStrong
            state == VpnState.Starting -> c.accent
            else -> c.onAccent
        },
        tween(110), label = "btnFg",
    )
    val borderColor by animateColorAsState(
        when {
            activeMain -> c.activeBorder
            state == VpnState.Starting -> c.accentBorder
            else -> Color.Transparent
        },
        tween(110), label = "btnBorder",
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
        enabled = state != VpnState.Starting,
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
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val titleRes = when (state) {
        VpnState.Active -> R.string.status_active
        VpnState.Starting -> R.string.status_starting
        is VpnState.Failed -> R.string.status_failed
        VpnState.Idle -> R.string.status_idle
    }
    Column(
        modifier
            .animateContentSize(tween(110))
            .clip(AppShapes.medium)
            .background(style.container.copy(alpha = 0.96f))
            .border(1.dp, style.border, AppShapes.medium)
            .padding(Spacing.space20),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = style.content,
            )
            if (state == VpnState.Starting) {
                Spacer(Modifier.size(10.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = style.content,
                )
            }
        }
        when (state) {
            is VpnState.Failed -> Text(
                state.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = style.content.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = Spacing.space4),
            )
            VpnState.Idle -> Text(
                stringResource(R.string.state_sub_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Spacing.space4),
            )
            VpnState.Starting -> Text(
                stringResource(R.string.btn_connecting),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                ),
                color = style.content,
                modifier = Modifier.padding(top = Spacing.space4),
            )
            VpnState.Active -> Text(
                timerText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                color = style.content,
                modifier = Modifier.padding(top = Spacing.space4),
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        Spacer(Modifier.height(10.dp))

        InfoRow(stringResource(R.string.row_protocol), protocol)
        InfoRow(
            stringResource(R.string.row_server),
            serverHost ?: stringResource(R.string.server_missing),
        )

        if (state is VpnState.Failed) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onRetry,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = style.content),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.action_retry), style = MaterialTheme.typography.labelMedium)
            }
        }
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
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
