package dev.triplet.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(store: RoutesStore, modifier: Modifier = Modifier) {
    val state by VpnController.state.collectAsState()
    val settings by store.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // System VPN consent: on approval retry the start sequence immediately,
    // so one Connect tap carries the user all the way to Active.
    val consentLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) VpnController.startNow(ctx)
    }

    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val statusText = when (state) {
            VpnState.Idle -> stringResource(R.string.status_idle)
            VpnState.Starting -> stringResource(R.string.status_starting)
            VpnState.Active -> stringResource(R.string.status_active)
            is VpnState.Failed -> (state as VpnState.Failed).reason
        }
        Text(statusText, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        settings?.let { s ->
            val vpn = s.routes.countValues(AppRoute.VPN)
            val dpi = s.routes.countValues(AppRoute.DPI)
            Text(stringResource(R.string.home_summary, vpn, dpi),
                 style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(32.dp))
        val enabled = state != VpnState.Starting
        Button(
            onClick = {
                scope.launch {
                    if (state == VpnState.Active) VpnController.stop(ctx)
                    else VpnController.start(ctx, consentLauncher::launch)
                }
            },
            enabled = enabled,
            modifier = Modifier.size(width = 220.dp, height = 56.dp),
        ) {
            Text(if (state == VpnState.Active) stringResource(R.string.btn_disconnect)
                 else stringResource(R.string.btn_connect))
        }
    }
}

private fun Map<String, AppRoute>.countValues(r: AppRoute) =
    values.count { it == r }
