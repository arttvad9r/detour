package dev.triplet.app.vpn

import android.content.Context
import android.content.Intent

class VpnRepository(
    private val context: Context,
) {
    val state = VpnController.state

    fun disconnect() {
        VpnController.stop(context)
    }

    fun connect(requestConsent: (Intent) -> Unit) {
        VpnController.start(context, requestConsent)
    }
}
