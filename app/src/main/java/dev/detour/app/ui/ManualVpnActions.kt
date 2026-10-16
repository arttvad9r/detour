package dev.detour.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.detour.app.R
import dev.detour.app.vpn.VpnController
import dev.detour.app.vpn.VpnState
import kotlinx.coroutines.launch

internal class ManualVpnActions(val start: () -> Unit, val stop: () -> Unit)

/** Shared explicit-connect permission flow for Home and guided setup. */
@Composable
internal fun rememberManualVpnActions(state: VpnState, snackbar: SnackbarHostState): ManualVpnActions {
    val context = LocalContext.current
    val tracker = rememberManualNotificationPermissionTracker(state, snackbar)
    val scope = rememberCoroutineScope()
    val deniedMessage = stringResource(R.string.err_vpn_permission)
    val consent = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) VpnController.startNow(context)
        else {
            tracker.cancel()
            scope.launch { snackbar.showSnackbar(deniedMessage) }
        }
    }
    return ManualVpnActions(
        start = { tracker.begin(); VpnController.start(context, consent::launch) },
        stop = { tracker.cancel(); VpnController.stop(context) },
    )
}
