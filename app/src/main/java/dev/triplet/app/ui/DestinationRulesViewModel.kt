package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.DestinationRule
import dev.triplet.app.core.MAX_DESTINATION_RULES
import dev.triplet.app.data.RoutesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class DestinationRulesError { DUPLICATE, LIMIT, SAVE }

data class DestinationRulesUiState(
    val rules: List<DestinationRule> = emptyList(),
    val saving: Boolean = false,
    val error: DestinationRulesError? = null,
)

class DestinationRulesViewModel(
    private val store: RoutesStore,
    private val restartTunnel: () -> Unit,
) : ViewModel() {
    private val saving = MutableStateFlow(false)
    private val error = MutableStateFlow<DestinationRulesError?>(null)
    private val mutationLock = Mutex()

    val uiState: StateFlow<DestinationRulesUiState> = combine(
        store.settings,
        saving,
        error,
    ) { settings, isSaving, currentError ->
        DestinationRulesUiState(
            rules = settings?.destinationRules.orEmpty(),
            saving = isSaving,
            error = currentError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DestinationRulesUiState(),
    )

    fun addRule(rule: DestinationRule) {
        mutate { current ->
            when {
                current.size >= MAX_DESTINATION_RULES -> MutationResult.Error(DestinationRulesError.LIMIT)
                current.any { it.type == rule.type && it.value == rule.value } ->
                    MutationResult.Error(DestinationRulesError.DUPLICATE)
                else -> MutationResult.Save(current + rule)
            }
        }
    }

    fun deleteRule(rule: DestinationRule) {
        mutate { current -> MutationResult.Save(current.filterNot { it == rule }) }
    }

    fun clearError() {
        error.value = null
    }

    private fun mutate(transform: (List<DestinationRule>) -> MutationResult) {
        viewModelScope.launch {
            mutationLock.withLock {
                saving.value = true
                try {
                    val current = store.snapshot().destinationRules
                    when (val result = transform(current)) {
                        is MutationResult.Error -> error.value = result.error
                        is MutationResult.Save -> {
                            store.setDestinationRules(result.rules)
                            error.value = null
                            restartTunnel()
                        }
                    }
                } catch (_: Exception) {
                    error.value = DestinationRulesError.SAVE
                } finally {
                    saving.value = false
                }
            }
        }
    }

    private sealed interface MutationResult {
        data class Save(val rules: List<DestinationRule>) : MutationResult
        data class Error(val error: DestinationRulesError) : MutationResult
    }

    companion object {
        fun factory(
            store: RoutesStore,
            restartTunnel: () -> Unit,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(DestinationRulesViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return DestinationRulesViewModel(store, restartTunnel) as T
            }
        }
    }
}
