package dev.triplet.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    private val controller: VpnControllerFacade = VpnControllerFacade.Default,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = controller.state
        .map { HomeUiState(vpnState = it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeUiState(),
        )

    fun onDisconnectRequested() {
        controller.disconnect()
    }

    interface Factory {
        fun create(): HomeViewModel
    }
}

interface VpnControllerFacade {
    val state: StateFlow<VpnState>

    fun disconnect()

    data object Default : VpnControllerFacade {
        override val state: StateFlow<VpnState> = VpnController.state

        override fun disconnect() {
            // Connection lifecycle still requires Context and remains owned by the
            // existing controller until repository extraction is completed.
        }
    }
}
