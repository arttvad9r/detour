package dev.detour.app.presentation

import dev.detour.app.vpn.VpnState

data class HomeUiState(
    val vpnState: VpnState = VpnState.Idle,
)
