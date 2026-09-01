package dev.triplet.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.triplet.app.core.DnsOptions
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

enum class DnsSaveState { IDLE, SAVING, ERROR }

data class DnsUiState(
    val selectedDns: String = "google",
    val customField: String = "",
    val editingCustom: Boolean = false,
    val customInvalid: Boolean = false,
    val customChanged: Boolean = false,
    val saveState: DnsSaveState = DnsSaveState.IDLE,
) {
    val canSaveCustom: Boolean
        get() = saveState != DnsSaveState.SAVING &&
            customField.isNotBlank() && !customInvalid && customChanged
}

internal fun persistedDnsSelection(settings: TriSettings?): String =
    settings?.dnsId?.ifBlank { null } ?: "google"

internal fun dnsUiState(
    settings: TriSettings?,
    customDraft: String?,
    editingOverride: Boolean?,
    saveState: DnsSaveState = DnsSaveState.IDLE,
    selectionOverride: String? = null,
): DnsUiState {
    val persistedCustom = settings?.dnsCustom.orEmpty()
    val customField = customDraft ?: persistedCustom
    val selectedDns = selectionOverride ?: persistedDnsSelection(settings)
    return DnsUiState(
        selectedDns = selectedDns,
        customField = customField,
        editingCustom = editingOverride ?: (selectedDns == DnsOptions.CUSTOM),
        customInvalid = customField.isNotBlank() && !DnsOptions.isValid(customField),
        customChanged =
            selectedDns != DnsOptions.CUSTOM || customField.trim() != persistedCustom.trim(),
        saveState = saveState,
    )
}

class DnsViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val setDns: suspend (String, String) -> Unit,
    private val restartTunnel: () -> Unit,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val customDraft = savedStateHandle.getStateFlow<String?>(KEY_CUSTOM_DRAFT, null)
    private val editingOverride = savedStateHandle.getStateFlow<Boolean?>(KEY_EDITING_CUSTOM, null)
    private val selectionOverride = MutableStateFlow<String?>(null)
    private val saveState = MutableStateFlow(DnsSaveState.IDLE)
    private val writeMutex = Mutex()
    private val _customSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val customSaved: SharedFlow<Unit> = _customSaved

    val uiState: StateFlow<DnsUiState> = combine(
        settings,
        customDraft,
        editingOverride,
        saveState,
        selectionOverride,
    ) { currentSettings, currentDraft, currentEditingOverride, currentSaveState, currentSelectionOverride ->
        dnsUiState(
            currentSettings,
            currentDraft,
            currentEditingOverride,
            currentSaveState,
            currentSelectionOverride,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = dnsUiState(settings.value, customDraft.value, editingOverride.value),
    )

    fun editCustom() {
        savedStateHandle[KEY_EDITING_CUSTOM] = true
    }

    fun setCustomField(value: String) {
        savedStateHandle[KEY_CUSTOM_DRAFT] = value
        if (saveState.value == DnsSaveState.ERROR) saveState.value = DnsSaveState.IDLE
    }

    fun chooseKnown(id: String) {
        require(id in DnsOptions.servers)
        if (saveState.value == DnsSaveState.SAVING) return
        saveState.value = DnsSaveState.IDLE
        savedStateHandle[KEY_EDITING_CUSTOM] = null

        val currentIntent = selectionOverride.value ?: persistedDnsSelection(settings.value)
        if (currentIntent == id) return
        selectionOverride.value = id

        viewModelScope.launch {
            writeMutex.withLock {
                val desired = selectionOverride.value ?: return@withLock
                if (desired == DnsOptions.CUSTOM) return@withLock
                try {
                    if (persistedDnsSelection(settings.value) != desired) {
                        setDns(desired, settings.value?.dnsCustom.orEmpty())
                        if (persistedDnsSelection(settings.value) != desired) {
                            settings.first { persistedDnsSelection(it) == desired }
                        }
                        restartTunnel()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (selectionOverride.value == desired) selectionOverride.value = null
                    return@withLock
                }

                if (selectionOverride.value == desired) selectionOverride.value = null
            }
        }
    }

    fun saveCustom() {
        val value = (customDraft.value ?: settings.value?.dnsCustom.orEmpty()).trim()
        if (!DnsOptions.isValid(value)) return
        val current = settings.value
        if (current?.dnsId == DnsOptions.CUSTOM && current.dnsCustom.trim() == value) return
        if (saveState.value == DnsSaveState.SAVING) return

        savedStateHandle[KEY_EDITING_CUSTOM] = true
        selectionOverride.value = DnsOptions.CUSTOM
        saveState.value = DnsSaveState.SAVING
        viewModelScope.launch {
            writeMutex.withLock {
                try {
                    setDns(DnsOptions.CUSTOM, value)
                    if (
                        persistedDnsSelection(settings.value) != DnsOptions.CUSTOM ||
                        settings.value?.dnsCustom?.trim() != value
                    ) {
                        settings.first {
                            persistedDnsSelection(it) == DnsOptions.CUSTOM &&
                                it?.dnsCustom?.trim() == value
                        }
                    }
                    restartTunnel()

                    if (customDraft.value?.trim() == value) {
                        savedStateHandle[KEY_CUSTOM_DRAFT] = null
                        if (editingOverride.value == true) savedStateHandle[KEY_EDITING_CUSTOM] = null
                    }
                    if (selectionOverride.value == DnsOptions.CUSTOM) selectionOverride.value = null
                    saveState.value = DnsSaveState.IDLE
                    _customSaved.emit(Unit)
                } catch (cancelled: CancellationException) {
                    if (selectionOverride.value == DnsOptions.CUSTOM) selectionOverride.value = null
                    saveState.value = DnsSaveState.IDLE
                    throw cancelled
                } catch (_: Exception) {
                    if (selectionOverride.value == DnsOptions.CUSTOM) selectionOverride.value = null
                    saveState.value = DnsSaveState.ERROR
                }
            }
        }
    }

    companion object {
        private const val KEY_CUSTOM_DRAFT = "dns_custom_draft"
        private const val KEY_EDITING_CUSTOM = "dns_editing_custom"

        fun factory(
            store: RoutesStore,
            restartTunnel: () -> Unit,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DnsViewModel(
                    settings = store.settings,
                    setDns = store::setDns,
                    restartTunnel = restartTunnel,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
