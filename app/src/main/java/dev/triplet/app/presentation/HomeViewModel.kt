package dev.triplet.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.triplet.app.vpn.VpnRepository
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    private val repository: VpnRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository.state
        .map { HomeUiState(vpnState = it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeUiState(VpnState.Idle),
        )

    fun onDisconnectRequested() {
        repository.disconnect()
    }

    fun onConnectRequested(requestConsent: (android.content.Intent) -> Unit) {
        repository.connect(requestConsent)
    }
}
