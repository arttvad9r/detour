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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.launch

private data class StatusStyle(val container: Color, val content: Color)

@Composable
fun HomeScreen(store: RoutesStore, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state by VpnController.state.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val st = state

    val style = when (st) {
        VpnState.Active -> StatusStyle(Color(0xFFDFF0E6), Color(0xFF1F9D5A))
        VpnState.Starting -> StatusStyle(Color(0xFFE4EAFC), Color(0xFF4C6EF5))
        is VpnState.Failed -> StatusStyle(Color(0xFFF9E4E4), Color(0xFFC94444))
        VpnState.Idle -> StatusStyle(Color(0xFFE9E4EF), Color(0xFF6E6679))
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
