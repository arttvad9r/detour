package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.DpiArgs
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class DpiSaveState { IDLE, SAVING, ERROR }

data class DpiUiState(
    val preset: DpiPreset = DpiPreset.RECOMMENDED,
    val customField: String = "",
    val editingCustom: Boolean = false,
    val customInvalid: Boolean = false,
    val customChanged: Boolean = false,
    val saveState: DpiSaveState = DpiSaveState.IDLE,
) {
    val canSaveCustom: Boolean
        get() = saveState != DpiSaveState.SAVING &&
            customField.isNotBlank() && !customInvalid && customChanged
}

internal fun dpiUiState(
    settings: TriSettings?,
    customDraft: String?,
    editingOverride: Boolean?,
    saveState: DpiSaveState = DpiSaveState.IDLE,
    presetOverride: DpiPreset? = null,
): DpiUiState {
    val persistedCustom = settings?.dpiCustomArgs.orEmpty()
    val customField = customDraft ?: persistedCustom
    val preset = presetOverride ?: settings?.preset ?: DpiPreset.RECOMMENDED
    return DpiUiState(
        preset = preset,
        customField = customField,
        editingCustom = editingOverride ?: (preset == DpiPreset.CUSTOM),
        customInvalid = customField.isNotBlank() && !DpiArgs.isValid(customField),
        customChanged = preset != DpiPreset.CUSTOM || customField.trim() != persistedCustom.trim(),
        saveState = saveState,
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
    private val presetOverride = MutableStateFlow<DpiPreset?>(null)
    private val saveState = MutableStateFlow(DpiSaveState.IDLE)
    private val writeMutex = Mutex()
    private val _customSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val customSaved: SharedFlow<Unit> = _customSaved

    val uiState: StateFlow<DpiUiState> = combine(
        settings,
        customDraft,
        editingOverride,
        saveState,
        presetOverride,
    ) { currentSettings, currentDraft, currentEditingOverride, currentSaveState, currentPresetOverride ->
        dpiUiState(
            currentSettings,
            currentDraft,
            currentEditingOverride,
            currentSaveState,
            currentPresetOverride,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = dpiUiState(settings.value, null, null),
    )

    fun editCustom() {
        editingOverride.value = true
    }

    fun setCustomField(value: String) {
        customDraft.value = value.replace("\r", " ").replace("\n", " ")
        if (saveState.value == DpiSaveState.ERROR) saveState.value = DpiSaveState.IDLE
    }

    fun chooseRecommended() {
        if (saveState.value == DpiSaveState.SAVING) return
        saveState.value = DpiSaveState.IDLE
        editingOverride.value = null

        val currentIntent = presetOverride.value ?: settings.value?.preset ?: DpiPreset.RECOMMENDED
        if (currentIntent == DpiPreset.RECOMMENDED) return
        presetOverride.value = DpiPreset.RECOMMENDED

        viewModelScope.launch {
            writeMutex.withLock {
                val desired = presetOverride.value ?: return@withLock
                if (desired != DpiPreset.RECOMMENDED) return@withLock
                try {
                    if (settings.value?.preset != desired) {
                        setPreset(desired)
                        if (settings.value?.preset != desired) {
                            settings.first { it?.preset == desired }
                        }
                        restartTunnel()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (presetOverride.value == desired) presetOverride.value = null
                    return@withLock
                }

                if (presetOverride.value == desired) presetOverride.value = null
            }
        }
    }

    fun saveCustom() {
        val value = (customDraft.value ?: settings.value?.dpiCustomArgs.orEmpty()).trim()
        if (!DpiArgs.isValid(value)) return
        val current = settings.value
        if (current?.preset == DpiPreset.CUSTOM && current.dpiCustomArgs.trim() == value) return
        if (saveState.value == DpiSaveState.SAVING) return

        editingOverride.value = true
        presetOverride.value = DpiPreset.CUSTOM
        saveState.value = DpiSaveState.SAVING
        viewModelScope.launch {
            writeMutex.withLock {
                try {
                    // Persist the validated draft before activating CUSTOM so a process
                    // death between writes cannot expose an invalid custom preset.
                    setCustomArgs(value)
                    setPreset(DpiPreset.CUSTOM)
                    if (
                        settings.value?.preset != DpiPreset.CUSTOM ||
                        settings.value?.dpiCustomArgs?.trim() != value
                    ) {
                        settings.first {
                            it?.preset == DpiPreset.CUSTOM &&
                                it.dpiCustomArgs.trim() == value
                        }
                    }
                    restartTunnel()

                    if (customDraft.value?.trim() == value) {
                        customDraft.value = null
                        if (editingOverride.value == true) editingOverride.value = null
                    }
                    if (presetOverride.value == DpiPreset.CUSTOM) presetOverride.value = null
                    saveState.value = DpiSaveState.IDLE
                    _customSaved.emit(Unit)
                } catch (cancelled: CancellationException) {
                    if (presetOverride.value == DpiPreset.CUSTOM) presetOverride.value = null
                    saveState.value = DpiSaveState.IDLE
                    throw cancelled
                } catch (_: Exception) {
                    if (presetOverride.value == DpiPreset.CUSTOM) presetOverride.value = null
                    saveState.value = DpiSaveState.ERROR
                }
            }
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
