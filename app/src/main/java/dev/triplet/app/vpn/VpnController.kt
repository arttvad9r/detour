package dev.triplet.app.vpn

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object VpnController {
    private val _state = MutableStateFlow<VpnState>(VpnState.Idle)
    val state: StateFlow<VpnState> = _state

    internal fun setState(s: VpnState) { _state.value = s }

    fun start(ctx: Context) =
        ctx.startForegroundService(intent(ctx, TriVpnService.ACTION_START))

    fun stop(ctx: Context) =
        ctx.startService(intent(ctx, TriVpnService.ACTION_STOP))

    fun restartIfActive(ctx: Context) {
        if (_state.value == VpnState.Active || _state.value == VpnState.Starting)
            ctx.startService(intent(ctx, TriVpnService.ACTION_RESTART))
    }

    private fun intent(ctx: Context, action: String): Intent =
        Intent(ctx, TriVpnService::class.java).setAction(action)
}
