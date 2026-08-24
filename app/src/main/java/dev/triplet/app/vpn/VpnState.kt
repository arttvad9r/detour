package dev.triplet.app.vpn

sealed interface VpnState {
    data object Idle : VpnState
    data object Starting : VpnState
    data object Active : VpnState
    data class Failed(val reason: String) : VpnState
}
