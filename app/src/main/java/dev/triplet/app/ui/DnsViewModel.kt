package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.DnsOptions
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DnsUiState(
    val selectedDns: String = "google",
    val customField: String = "",
    val editingCustom: Boolean = false,
    val customInvalid: Boolean = false,
) {
    val canSaveCustom: Boolean get() = customField.isNotBlank() && !customInvalid
}

internal fun dnsUiState(
    settings: TriSettings?,
    customDraft: String?,
    editingOverride: Boolean?,
): DnsUiState {
    val customField = customDraft ?: settings?.dnsCustom.orEmpty()
    return DnsUiState(
        selectedDns = settings?.dnsId?.ifBlank { null } ?: "google",
        customField = customField,
        editingCustom = editingOverride ?: (settings?.dnsId == DnsOptions.CUSTOM),
        customInvalid = customField.isNotBlank() && !DnsOptions.isValid(customField),
    )
}

class DnsViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val setDns: suspend (String, String) -> Unit,
    private val restartTunnel: () -> Unit,
) : ViewModel() {
    private val customDraft = MutableStateFlow<String?>(null)
    private val editingOverride = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<DnsUiState> = combine(
        settings,
        customDraft,
        editingOverride,
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
    }

    fun chooseKnown(id: String) {
        require(id in DnsOptions.servers)
        editingOverride.value = false
        if (uiState.value.selectedDns == id) return
        val persistedCustom = settings.value?.dnsCustom.orEmpty()
        viewModelScope.launch {
            setDns(id, persistedCustom)
            restartTunnel()
        }
    }

    fun saveCustom() {
        val value = uiState.value.customField.trim()
        if (!DnsOptions.isValid(value)) return
        viewModelScope.launch {
            setDns(DnsOptions.CUSTOM, value)
            customDraft.value = value
            editingOverride.value = true
            restartTunnel()
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
