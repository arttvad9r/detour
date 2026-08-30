package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

internal fun dnsUiState(
    settings: TriSettings?,
    customDraft: String?,
    editingOverride: Boolean?,
    saveState: DnsSaveState = DnsSaveState.IDLE,
): DnsUiState {
    val persistedCustom = settings?.dnsCustom.orEmpty()
    val customField = customDraft ?: persistedCustom
    val selectedDns = settings?.dnsId?.ifBlank { null } ?: "google"
    return DnsUiState(
        selectedDns = selectedDns,
        customField = customField,
        editingCustom = editingOverride ?: (settings?.dnsId == DnsOptions.CUSTOM),
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
) : ViewModel() {
    private val customDraft = MutableStateFlow<String?>(null)
    private val editingOverride = MutableStateFlow<Boolean?>(null)
    private val saveState = MutableStateFlow(DnsSaveState.IDLE)
    private val _customSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val customSaved: SharedFlow<Unit> = _customSaved

    val uiState: StateFlow<DnsUiState> = combine(
        settings,
        customDraft,
        editingOverride,
        saveState,
        ::dnsUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = dnsUiState(settings.value, null, null),
    )

    fun editCustom() {
        editingOverride.value = true
    }

    fun setCustomField(value: String) {
        customDraft.value = value
        if (saveState.value == DnsSaveState.ERROR) saveState.value = DnsSaveState.IDLE
    }

    fun chooseKnown(id: String) {
        require(id in DnsOptions.servers)
        if (saveState.value == DnsSaveState.SAVING) return
        editingOverride.value = false
        saveState.value = DnsSaveState.IDLE
        val selectedDns = settings.value?.dnsId?.ifBlank { null } ?: "google"
        if (selectedDns == id) return
        val persistedCustom = settings.value?.dnsCustom.orEmpty()
        viewModelScope.launch {
            setDns(id, persistedCustom)
            restartTunnel()
        }
    }

    fun saveCustom() {
        val value = (customDraft.value ?: settings.value?.dnsCustom.orEmpty()).trim()
        if (!DnsOptions.isValid(value)) return
        val current = settings.value
        if (current?.dnsId == DnsOptions.CUSTOM && current.dnsCustom.trim() == value) return
        if (saveState.value == DnsSaveState.SAVING) return
        saveState.value = DnsSaveState.SAVING
        viewModelScope.launch {
            try {
                setDns(DnsOptions.CUSTOM, value)
                if (customDraft.value?.trim() == value) customDraft.value = value
                editingOverride.value = true
                restartTunnel()
                saveState.value = DnsSaveState.IDLE
                _customSaved.emit(Unit)
            } catch (cancelled: CancellationException) {
                saveState.value = DnsSaveState.IDLE
                throw cancelled
            } catch (_: Exception) {
                saveState.value = DnsSaveState.ERROR
            }
        }
    }

    companion object {
        fun factory(
            store: RoutesStore,
            restartTunnel: () -> Unit,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(DnsViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return DnsViewModel(
                    settings = store.settings,
                    setDns = store::setDns,
                    restartTunnel = restartTunnel,
                ) as T
            }
        }
    }
}
