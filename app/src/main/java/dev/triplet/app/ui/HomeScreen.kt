package dev.triplet.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import dev.triplet.app.R
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.delay

/**
 * Главный экран: header, воздух, компактная статус-карточка, горная сцена,
 * кнопка действия. Экран — функция VpnState; логика в VpnController.
 */
@Composable
fun HomeScreen(store: RoutesStore, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state by VpnController.state.collectAsState()
    val ctx = LocalContext.current
    val st = state
    val c = detourColors
    val theme = LocalDetourTheme.current

    val settings by store.settings.collectAsState(initial = null)
    val status = theme.statusFor(st)
    // Плавный переход OFF -> CONNECTING -> ON (и обратно) за ~400мс.
    val style = StatusStyle(
        container = animateColorAsState(status.container, tween(400), label = "cardBg").value,
        content = animateColorAsState(status.content, tween(400), label = "cardFg").value,
        border = animateColorAsState(status.border, tween(400), label = "cardBorder").value,
    )

    // Таймер: тикает в Starting (длительность подключения) и Active (сессия).
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(st) {
        if (st == VpnState.Active || st == VpnState.Starting) {
            elapsed = 0
            while (true) {
                delay(1000)
                elapsed += 1
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
    val strategyLabel = stringResource(
        if (settings?.preset == DpiPreset.CUSTOM) R.string.preset_custom else R.string.preset_recommended,
    )

    // Системное согласие VPN: после одобрения сразу продолжаем подключение.
    val consentLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) VpnController.startNow(ctx)
    }
    val onMainAction: () -> Unit = {
        if (st == VpnState.Active) VpnController.stop(ctx)
        else VpnController.start(ctx, consentLauncher::launch)
    }

    Box(modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header: Detour + настройки. Иконка 22dp, touch target 48dp.
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.space24, end = Spacing.space8, top = Spacing.space8),
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

            // Воздух намеренный: карточка в верхней трети свободного поля,
            // горная сцена — между карточкой и кнопкой.
            Spacer(Modifier.weight(1f))
            StatusCard(
                state = st,
                style = style,
                timerText = timerText,
                serverHost = serverHost,
                strategyLabel = strategyLabel,
                onRetry = onMainAction,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space24)
                    .widthIn(max = 360.dp),
            )
            Spacer(Modifier.weight(1.35f))

            Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
                MainButton(
                    state = st,
                    onClick = onMainAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.space32, vertical = Spacing.space20),
                )
            }
        }
    }
}
/** Главная кнопка: 52dp, состояния OFF/CONNECTING/ON различаются мягко. */
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
        tween(400), label = "btnBg",
    )
    val content by animateColorAsState(
        when {
            activeMain -> c.activeStrong
            state == VpnState.Starting -> c.accent
            else -> c.onAccent
        },
        tween(400), label = "btnFg",
    )
    val borderColor by animateColorAsState(
        when {
            activeMain -> c.activeBorder
            state == VpnState.Starting -> c.accentBorder
            else -> Color.Transparent
        },
        tween(400), label = "btnBorder",
    )
    val text = when (state) {
        VpnState.Active -> stringResource(R.string.btn_disconnect)
        VpnState.Starting -> stringResource(R.string.btn_connecting)
        else -> stringResource(R.string.btn_connect)
    }
    Box(
        modifier
            .background(Color.Transparent)
            .clip(PillShape)
            .background(container)
            .border(1.dp, borderColor, PillShape)
            .clickable(enabled = state != VpnState.Starting, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = Spacing.space24, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = content,
        )
    }
}

/** Компактная статус-карточка: статус 18sp, таймер, разделитель, сетка данных. */
@Composable
private fun StatusCard(
    state: VpnState,
    style: StatusStyle,
    timerText: String,
    serverHost: String?,
    strategyLabel: String,
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
            .clip(AppShapes.medium)
            .background(style.container.copy(alpha = 0.96f))
            .border(1.dp, style.border, AppShapes.medium)
            .padding(Spacing.space20),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = style.content,
            )
            if (state == VpnState.Starting) {
                Spacer(Modifier.size(10.dp))
                // Маленький inline-индикатор вместо огромного спиннера.
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
            else -> Text(
                timerText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    fontSize = 14.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = style.content,
                modifier = Modifier.padding(top = Spacing.space4),
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        Spacer(Modifier.height(10.dp))

        InfoRow(stringResource(R.string.row_protocol), "VLESS")
        InfoRow(
            stringResource(R.string.row_server),
            serverHost ?: stringResource(R.string.server_missing),
        )
        InfoRow(stringResource(R.string.row_strategy), strategyLabel)

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

/** Двухколоночная сетка параметров: подпись secondary, значение Medium. */
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
