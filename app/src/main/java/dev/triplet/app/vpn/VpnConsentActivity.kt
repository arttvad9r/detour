package dev.triplet.app.vpn

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult

/** ActivityResult owner for VPN consent; TileService cannot receive this result. */
class VpnConsentActivity : ComponentActivity() {
    private val consentLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) VpnController.startNow(this)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = VpnService.prepare(this)
        if (intent == null) {
            VpnController.startNow(this)
            finish()
        } else {
            consentLauncher.launch(intent)
        }
    }
}
