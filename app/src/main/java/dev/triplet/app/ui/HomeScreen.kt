package dev.triplet.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triplet.app.R
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(store: RoutesStore, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state by VpnController.state.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val st = state

    val settings by store.settings.collectAsState(initial = null)
    val theme = AppTheme.byId(settings?.themeId ?: "")
    val style = theme.statusFor(st)
    // Таймер сессии: тикает, пока туннель активен.
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(st) {
        if (st == VpnState.Active) {
            while (true) {
                elapsed += 1
                kotlinx.coroutines.delay(1000)
            }
        } else elapsed = 0
    }
    val timerText = remember(elapsed) {
        val h = elapsed / 3600; val m = (elapsed % 3600) / 60; val sec = elapsed % 60
        "%02d:%02d:%02d".format(h, m, sec)
    }
    val serverHost = remember(settings?.vlessUri) {
        settings?.vlessUri?.let { VlessKeyParser.parse(it) }.let { r ->
            (r as? ParseResult.Ok)?.profile?.server
        }
    }
    val titleRes = when (st) {
        VpnState.Active -> R.string.status_active
        VpnState.Starting -> R.string.status_starting
        is VpnState.Failed -> R.string.status_failed
        VpnState.Idle -> R.string.status_idle
    }

    // Системное согласие VPN: после одобрения сразу продолжаем подключение,
    // чтобы от одного тапа «Подключить» пользователь приходил к Active.
    val consentLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) VpnController.startNow(ctx)
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 14.dp)) {
            Text(
                stringResource(R.string.app_name),
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    painter = painterResource(R.drawable.ic_gear),
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .background(style.container, RoundedCornerShape(22.dp))
                    .padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(titleRes),
                    fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp, color = style.content,
                )
                if (st == VpnState.Active) {
                    Text(
                        timerText,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = style.content, letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    serverHost?.let {
                        Text(
                            "через VLESS · $it",
                            fontSize = 11.5.sp, color = style.content.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                if (st is VpnState.Failed) {
                    Text(
                        st.reason,
                        fontSize = 12.5.sp, color = style.content,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    if (st == VpnState.Active) VpnController.stop(ctx)
                    else VpnController.start(ctx, consentLauncher::launch)
                }
            },
            enabled = st != VpnState.Starting,
            colors = if (st == VpnState.Active) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFFC94444),
            ) else ButtonDefaults.buttonColors(),
            modifier = Modifier.align(Alignment.CenterHorizontally)
                .padding(bottom = 44.dp).fillMaxWidth(0.72f).height(54.dp),
        ) {
            Text(
                if (st == VpnState.Active) stringResource(R.string.btn_disconnect)
                else stringResource(R.string.btn_connect),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
            )
        }
    }
}
