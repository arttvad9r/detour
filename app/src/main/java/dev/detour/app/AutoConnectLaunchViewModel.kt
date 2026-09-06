package dev.detour.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

internal class AutoConnectLaunchViewModel : ViewModel() {
    private var launched = false

    internal fun claimLaunch(): Boolean {
        if (launched) return false
        launched = true
        return true
    }

    fun launchOnce(block: suspend () -> Unit) {
        if (!claimLaunch()) return
        viewModelScope.launch { block() }
    }
}
