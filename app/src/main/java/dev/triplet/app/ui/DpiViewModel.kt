package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.DpiArgs
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DpiUiState(
    val preset: DpiPreset = DpiPreset.RECOMMENDED,
    val customField: String = "",
    val editingCustom: Boolean = false,
    val customInvalid: Boolean = false,
) {
    val canSaveCustom: Boolean get() = customField.isNotBlank() && !customInvalid
}

internal fun dpiUiState(
    settings: TriSettings?,
    customDraft: String?,
    editingOverride: Boolean?,
): DpiUiState {
    val customField = customDraft ?: settings?.dpiCustomArgs.orEmpty()
    return DpiUiState(
        preset = settings?.preset ?: DpiPreset.RECOMMENDED,
        customField = customField,
        editingCustom = editingOverride ?: (settings?.preset == DpiPreset.CUSTOM),
        customInvalid = customField.isNotBlank() && !DpiArgs.isValid(customField),
    )
}

class DpiViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val setPreset: suspend (DpiPreset) -> Unit,
    private val setCustomArgs: suspend (String) -> Unit,
    private val restartTunnel: () -> Unit,
) : ViewModel() {
    private val customDraft = MutableStateFlow<String?>(null)
    private val editingOverride = MutableStateFlow<Boolean?>(null)
    private val _customSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val customSaved: SharedFlow<Unit> = _customSaved

    val uiState: StateFlow<DpiUiState> = combine(
        settings,
        customDraft,
        editingOverride,
        ::dpiUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = dpiUiState(settings.value, null, null),
    )

    fun editCustom() {
        editingOverride.value = true
    }

    fun setCustomField(value: String) {
        customDraft.value = value.replace("\r", " ").replace("\n", " ")
    }

    fun chooseRecommended() {
        editingOverride.value = false
        if (uiState.value.preset == DpiPreset.RECOMMENDED) return
        viewModelScope.launch {
            setPreset(DpiPreset.RECOMMENDED)
            restartTunnel()
        }
    }

    fun saveCustom() {
        val value = uiState.value.customField.trim()
        if (!DpiArgs.isValid(value)) return
        viewModelScope.launch {
            // Persist the validated draft before activating CUSTOM so a process
            // death between writes cannot expose an invalid custom preset.
            setCustomArgs(value)
            setPreset(DpiPreset.CUSTOM)
            customDraft.value = value
            editingOverride.value = true
            restartTunnel()
            _customSaved.emit(Unit)
        }
    }

    companion object {
        fun factory(
            store: RoutesStore,
            restartTunnel: () -> Unit,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(DpiViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return DpiViewModel(
                    settings = store.settings,
                    setPreset = store::setPreset,
                    setCustomArgs = store::setCustomArgs,
                    restartTunnel = restartTunnel,
                ) as T
            }
        }
    }
}
