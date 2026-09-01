package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.SubscriptionProviderState
import dev.triplet.app.core.SubscriptionProviderStateParser
import dev.triplet.engine.engine.Engine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SubscriptionRuntimeStatus { IDLE, LOADING, REFRESHING, ERROR }

data class SubscriptionRuntimeUiState(
    val provider: SubscriptionProviderState = SubscriptionProviderState.Unavailable,
    val status: SubscriptionRuntimeStatus = SubscriptionRuntimeStatus.IDLE,
)

class SubscriptionRuntimeViewModel : ViewModel() {
    private val operationMutex = Mutex()
    private val _uiState = MutableStateFlow(SubscriptionRuntimeUiState())
    val uiState: StateFlow<SubscriptionRuntimeUiState> = _uiState.asStateFlow()

    fun load() {
        runOperation(refresh = false)
    }

    fun refresh() {
        runOperation(refresh = true)
    }

    private fun runOperation(refresh: Boolean) {
        if (_uiState.value.status == SubscriptionRuntimeStatus.REFRESHING) return
        viewModelScope.launch {
            operationMutex.withLock {
                try {
                    _uiState.value = _uiState.value.copy(
                        status = if (refresh) {
                            SubscriptionRuntimeStatus.REFRESHING
                        } else {
                            SubscriptionRuntimeStatus.LOADING
                        },
                    )
                    val provider = withContext(Dispatchers.IO) {
                        if (refresh) Engine.refreshSubscriptionProvider()
                        SubscriptionProviderStateParser.parse(Engine.subscriptionProviderState())
                    }
                    _uiState.value = SubscriptionRuntimeUiState(
                        provider = provider,
                        status = SubscriptionRuntimeStatus.IDLE,
                    )
                } catch (cancelled: CancellationException) {
                    _uiState.value = _uiState.value.copy(status = SubscriptionRuntimeStatus.IDLE)
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the last known provider snapshot. A failed refresh must not
                    // blank useful health information or alter the running tunnel.
                    _uiState.value = _uiState.value.copy(status = SubscriptionRuntimeStatus.ERROR)
                }
            }
        }
    }
}
