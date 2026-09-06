package dev.detour.app.vpn

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object VpnController {
    private val _state = MutableStateFlow<VpnState>(VpnState.Idle)
    val state: StateFlow<VpnState> = _state

    internal fun setState(s: VpnState) { _state.value = s }

    fun startNow(ctx: Context) =
        ctx.startForegroundService(intent(ctx, TriVpnService.ACTION_START))

    fun start(ctx: Context, requestConsent: (Intent) -> Unit) {
        // First connect: the system consent dialog must be approved by the
        // user; the caller relaunches startNow() on RESULT_OK.
        val consent = android.net.VpnService.prepare(ctx)
        if (consent != null) {
            dev.detour.app.log.ServiceLog.i("vpn: requesting user consent")
            requestConsent(consent)
            return
        }
        startNow(ctx)
    }

    fun stop(ctx: Context) =
        ctx.startService(intent(ctx, TriVpnService.ACTION_STOP))

    fun restartIfActive(ctx: Context) {
        if (_state.value == VpnState.Active || _state.value == VpnState.Starting)
            ctx.startService(intent(ctx, TriVpnService.ACTION_RESTART))
    }

    private fun intent(ctx: Context, action: String): Intent =
        Intent(ctx, TriVpnService::class.java)
            .setAction(action)
            .putExtra(DETOUR_VPN_EXTRA_STARTED_BY_APP, true)
}
