package dev.triplet.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.triplet.app.R
import dev.triplet.app.vpn.VpnState

enum class NotificationPermissionPrompt { NONE, REQUEST, RATIONALE }

internal fun notificationPermissionPrompt(
    sdkInt: Int,
    granted: Boolean,
    shouldShowRationale: Boolean,
    directRequestAttempted: Boolean,
    rationaleShown: Boolean,
): NotificationPermissionPrompt = when {
    sdkInt < 33 || granted -> NotificationPermissionPrompt.NONE
    shouldShowRationale && !rationaleShown -> NotificationPermissionPrompt.RATIONALE
    directRequestAttempted -> NotificationPermissionPrompt.NONE
    else -> NotificationPermissionPrompt.REQUEST
}

internal class ManualNotificationPermissionTracker(
    val begin: () -> Unit,
    val cancel: () -> Unit,
)

@Composable
internal fun rememberManualNotificationPermissionTracker(
    vpnState: VpnState,
    snackbarHostState: SnackbarHostState,
): ManualNotificationPermissionTracker {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val rationaleMessage = stringResource(R.string.vpn_notification_permission_rationale)
    val allowLabel = stringResource(R.string.vpn_notification_permission_action)
    var pendingManualConnect by rememberSaveable { mutableStateOf(false) }
    var directRequestAttempted by rememberSaveable { mutableStateOf(false) }
    var rationaleShown by rememberSaveable { mutableStateOf(false) }
    var previousVpnState by remember { mutableStateOf<VpnState?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { }

    LaunchedEffect(vpnState) {
        val previous = previousVpnState
        previousVpnState = vpnState
        if (previous == null) return@LaunchedEffect

        if (vpnState is VpnState.Failed || previous == VpnState.Starting && vpnState == VpnState.Idle) {
            pendingManualConnect = false
            return@LaunchedEffect
        }
        if (vpnState != VpnState.Active || previous == VpnState.Active || !pendingManualConnect) {
            return@LaunchedEffect
        }

        pendingManualConnect = false
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        val shouldShowRationale = Build.VERSION.SDK_INT >= 33 &&
            activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            } == true

        when (
            notificationPermissionPrompt(
                sdkInt = Build.VERSION.SDK_INT,
                granted = granted,
                shouldShowRationale = shouldShowRationale,
                directRequestAttempted = directRequestAttempted,
                rationaleShown = rationaleShown,
            )
        ) {
            NotificationPermissionPrompt.NONE -> Unit
            NotificationPermissionPrompt.REQUEST -> {
                directRequestAttempted = true
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            NotificationPermissionPrompt.RATIONALE -> {
                rationaleShown = true
                if (
                    snackbarHostState.showSnackbar(
                        message = rationaleMessage,
                        actionLabel = allowLabel,
                        withDismissAction = true,
                    ) == SnackbarResult.ActionPerformed
                ) {
                    directRequestAttempted = true
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    return remember {
        ManualNotificationPermissionTracker(
            begin = { pendingManualConnect = true },
            cancel = { pendingManualConnect = false },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
