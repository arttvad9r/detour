package dev.triplet.app.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle

/** ActivityResult owner for VPN consent; TileService cannot receive this result. */
class VpnConsentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = VpnService.prepare(this)
        if (intent == null) {
            VpnController.startNow(this)
            finish()
        } else {
            startActivityForResult(intent, REQUEST_CONSENT)
        }
    }

    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONSENT && resultCode == RESULT_OK) VpnController.startNow(this)
        finish()
    }

    private companion object { const val REQUEST_CONSENT = 41 }
}
