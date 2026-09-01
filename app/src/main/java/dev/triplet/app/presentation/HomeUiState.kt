package dev.triplet.app.presentation

import dev.triplet.app.vpn.VpnState

data class HomeUiState(
    val vpnState: VpnState = VpnState.Idle,
)
