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
) : ViewModel() {
    private val customDraft = MutableStateFlow<String?>(null)
    private val editingOverride = MutableStateFlow<Boolean?>(null)
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
        saveState.value = DnsSaveState.IDLE
        editingOverride.value = null

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

        editingOverride.value = true
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
                        customDraft.value = null
                        if (editingOverride.value == true) editingOverride.value = null
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
