package dev.triplet.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
 * Главный экран: заголовок + настройки, компактная статус-карточка,
 * горный фон в нижней трети и широкая pill-кнопка действия.
 * Экран — простая функция VpnState; логика подключения в VpnController.
 */
@Composable
fun HomeScreen(store: RoutesStore, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state by VpnController.state.collectAsState()
    val ctx = LocalContext.current
    val st = state

    val settings by store.settings.collectAsState(initial = null)
    val theme = AppTheme.byId(settings?.themeId ?: "")
    val statusStyle = theme.statusFor(st)
    // Плавный переход OFF -> CONNECTING -> ON (и обратно) за ~350мс.
    val style = StatusStyle(
        container = animateColorAsState(statusStyle.container, tween(350), label = "cardBg").value,
        content = animateColorAsState(statusStyle.content, tween(350), label = "cardFg").value,
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

    Box(modifier.fillMaxSize()) {
        MountainBackground(theme.mountains, st, Modifier.matchParentSize())

        Column(Modifier.fillMaxSize()) {
            // Верх: Detour слева, настройки справа. Без toolbar.
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        painter = painterResource(R.drawable.ic_gear),
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Воздух намеренный: карточка одна, по центру свободного поля.
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                StatusCard(
                    state = st,
                    style = style,
                    timerText = timerText,
                    serverHost = serverHost,
                    strategyLabel = strategyLabel,
                    onRetry = onMainAction,
                    modifier = Modifier.fillMaxWidth(0.82f),
                )
            }

            val activeMain = st == VpnState.Active
            PillButton(
                text = when (st) {
                    VpnState.Active -> stringResource(R.string.btn_disconnect)
                    VpnState.Starting -> stringResource(R.string.btn_connecting)
                    else -> stringResource(R.string.btn_connect)
                },
                onClick = onMainAction,
                enabled = st != VpnState.Starting,
                container = animateColorAsState(
                    if (activeMain) theme.statusOn.first else MaterialTheme.colorScheme.primary,
                    tween(350), label = "btnBg",
                ).value,
                content = animateColorAsState(
                    if (activeMain) theme.statusOn.second else MaterialTheme.colorScheme.onPrimary,
                    tween(350), label = "btnFg",
                ).value,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 44.dp),
            )
        }
    }
}

/** Компактная статус-карточка: статус, таймер, разделитель, строки реальных данных. */
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
    val titleRes = when (state) {
        VpnState.Active -> R.string.status_active
        VpnState.Starting -> R.string.status_starting
        is VpnState.Failed -> R.string.status_failed
        VpnState.Idle -> R.string.status_idle
    }
    Column(
        modifier
            .background(style.container.copy(alpha = 0.92f), RoundedCornerShape(24.dp))
            .border(1.dp, hairline(), RoundedCornerShape(24.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Text(
            stringResource(titleRes),
            fontSize = 20.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp, color = style.content,
        )
        when (state) {
            is VpnState.Failed -> Text(
                state.reason,
                fontSize = 13.sp, color = style.content.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 4.dp),
            )
            VpnState.Idle -> Text(
                stringResource(R.string.state_sub_idle),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            else -> Text(
                timerText,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                style = TextStyle(fontFeatureSettings = "tnum"),
                color = style.content,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.height(12.dp))

        InfoRow(stringResource(R.string.row_protocol), "VLESS")
        InfoRow(stringResource(R.string.row_server), serverHost ?: stringResource(R.string.server_missing))
        InfoRow(stringResource(R.string.row_strategy), strategyLabel)

        if (state is VpnState.Failed) {
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = style.content),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    stringResource(R.string.action_retry),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.38f),
        )
        Text(
            value, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
