package dev.triplet.app.vpn

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** ActivityResult owner for VPN consent and notification permission; TileService cannot receive either result. */
class VpnConsentActivity : ComponentActivity() {
    private val notificationLauncher = registerForActivityResult(RequestPermission()) {
        startVpnAndFinish()
    }

    private val consentLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) continueAfterVpnConsent() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ActivityResultRegistry restores an in-flight VPN/notification request
        // across recreation. Relaunching here would create a duplicate prompt.
        if (savedInstanceState != null) return

        val intent = VpnService.prepare(this)
        if (intent == null) continueAfterVpnConsent() else consentLauncher.launch(intent)
    }

    private fun continueAfterVpnConsent() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        val userAlreadyDenied = needsNotificationPermission && Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            )

        if (needsNotificationPermission && !userAlreadyDenied) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Do not nag from Quick Settings after a denial. Home owns the
            // explanatory rationale flow if the user wants to enable notifications later.
            startVpnAndFinish()
        }
    }

    private fun startVpnAndFinish() {
        // Notification permission is not required to run a foreground service.
        // A denial must not silently turn a user's explicit VPN action into a no-op.
        VpnController.startNow(this)
        finish()
    }
}
