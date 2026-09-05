package dev.triplet.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.triplet.app.core.DpiArgs
import dev.triplet.app.core.DpiAutoSearchReport
import dev.triplet.app.core.DpiDomainCatalog
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.DpiProbeTarget
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import dev.triplet.app.vpn.VpnState
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class DpiSaveState { IDLE, SAVING, ERROR }
enum class DpiAutoRunState { IDLE, RUNNING, CANCELLING, COMPLETE, APPLYING, ERROR }

data class DpiUiState(
    val preset: DpiPreset = DpiPreset.RECOMMENDED,
    val customField: String = "",
    val editingCustom: Boolean = false,
    val customInvalid: Boolean = false,
    val customChanged: Boolean = false,
    val saveState: DpiSaveState = DpiSaveState.IDLE,
    val editingAuto: Boolean = false,
    val selectedAutoGroups: Set<String> = DpiDomainCatalog.default.map { it.id }.toSet(),
    val autoRunState: DpiAutoRunState = DpiAutoRunState.IDLE,
    val autoReport: DpiAutoSearchReport? = null,
    val vpnIdle: Boolean = true,
    val appliedAutoCandidateId: String = "",
) {
    val canSaveCustom: Boolean
        get() = saveState != DpiSaveState.SAVING &&
            customField.isNotBlank() && !customInvalid && customChanged

    val canRunAuto: Boolean
        get() = vpnIdle && selectedAutoGroups.isNotEmpty() &&
            autoRunState != DpiAutoRunState.RUNNING &&
            autoRunState != DpiAutoRunState.CANCELLING &&
            autoRunState != DpiAutoRunState.APPLYING

    val canApplyAuto: Boolean
        get() {
            val winnerId = autoReport?.winner?.candidate?.id ?: return false
            return autoRunState == DpiAutoRunState.COMPLETE &&
                !(preset == DpiPreset.AUTO && appliedAutoCandidateId == winnerId)
        }
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
        appliedAutoCandidateId = settings?.dpiAutoCandidateId.orEmpty(),
    )
}

private data class DpiAutoPresentation(
    val editingOverride: Boolean?,
    val selectedGroups: Set<String>,
    val runState: DpiAutoRunState,
    val report: DpiAutoSearchReport?,
    val vpnState: VpnState,
)

class DpiViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val setPreset: suspend (DpiPreset) -> Unit,
    private val setCustomArgs: suspend (String) -> Unit,
    private val setAutoCandidateId: suspend (String) -> Unit,
    private val vpnState: StateFlow<VpnState>,
    private val runAutoSearch: suspend (List<DpiProbeTarget>, () -> Boolean) -> DpiAutoSearchReport,
    private val restartTunnel: () -> Unit,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val customDraft = savedStateHandle.getStateFlow<String?>(KEY_CUSTOM_DRAFT, null)
    private val editingOverride = savedStateHandle.getStateFlow<Boolean?>(KEY_EDITING_CUSTOM, null)
    private val editingAutoOverride = savedStateHandle.getStateFlow<Boolean?>(KEY_EDITING_AUTO, null)
    private val selectedAutoGroups = savedStateHandle.getStateFlow(
        KEY_AUTO_GROUPS,
        DpiDomainCatalog.default.joinToString(",") { it.id },
    )
    private val presetOverride = MutableStateFlow<DpiPreset?>(null)
    private val saveState = MutableStateFlow(DpiSaveState.IDLE)
    private val autoRunState = MutableStateFlow(DpiAutoRunState.IDLE)
    private val autoReport = MutableStateFlow<DpiAutoSearchReport?>(null)
    private val autoGeneration = AtomicInteger(0)
    private val writeMutex = Mutex()
    private val _customSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val customSaved: SharedFlow<Unit> = _customSaved
    private val _autoApplied = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val autoApplied: SharedFlow<Unit> = _autoApplied

    private val baseUiState = combine(
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
    }

    private val autoPresentation = combine(
        editingAutoOverride,
        selectedAutoGroups,
        autoRunState,
        autoReport,
        vpnState,
    ) { editingAuto, groupCsv, runState, report, currentVpnState ->
        DpiAutoPresentation(
            editingOverride = editingAuto,
            selectedGroups = decodeGroupIds(groupCsv),
            runState = runState,
            report = report,
            vpnState = currentVpnState,
        )
    }

    val uiState: StateFlow<DpiUiState> = combine(baseUiState, autoPresentation) { base, auto ->
        val editingAuto = auto.editingOverride ?: (base.preset == DpiPreset.AUTO)
        base.copy(
            editingCustom = if (editingAuto) false else base.editingCustom,
            editingAuto = editingAuto,
            selectedAutoGroups = auto.selectedGroups,
            autoRunState = auto.runState,
            autoReport = auto.report,
            vpnIdle = auto.vpnState == VpnState.Idle,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = dpiUiState(settings.value, customDraft.value, editingOverride.value).copy(
            editingAuto = editingAutoOverride.value ?: (settings.value?.preset == DpiPreset.AUTO),
            selectedAutoGroups = decodeGroupIds(selectedAutoGroups.value),
            vpnIdle = vpnState.value == VpnState.Idle,
        ),
    )

    fun editCustom() {
        cancelAutoTest(resetReport = false)
        savedStateHandle[KEY_EDITING_AUTO] = false
        savedStateHandle[KEY_EDITING_CUSTOM] = true
    }

    fun editAuto() {
        if (saveState.value == DpiSaveState.SAVING) return
        savedStateHandle[KEY_EDITING_CUSTOM] = false
        savedStateHandle[KEY_EDITING_AUTO] = true
    }

    fun setCustomField(value: String) {
        savedStateHandle[KEY_CUSTOM_DRAFT] = value.replace("\r", " ").replace("\n", " ")
        if (saveState.value == DpiSaveState.ERROR) saveState.value = DpiSaveState.IDLE
    }

    fun toggleAutoGroup(id: String) {
        if (
            autoRunState.value == DpiAutoRunState.RUNNING ||
            autoRunState.value == DpiAutoRunState.CANCELLING ||
            autoRunState.value == DpiAutoRunState.APPLYING
        ) return
        if (DpiDomainCatalog.default.none { it.id == id }) return
        val next = decodeGroupIds(selectedAutoGroups.value).toMutableSet()
        if (!next.add(id)) next.remove(id)
        savedStateHandle[KEY_AUTO_GROUPS] = DpiDomainCatalog.default
            .map { it.id }
            .filter { it in next }
            .joinToString(",")
        autoReport.value = null
        if (autoRunState.value != DpiAutoRunState.IDLE) autoRunState.value = DpiAutoRunState.IDLE
    }

    fun startAutoTest() {
        if (
            vpnState.value != VpnState.Idle ||
            autoRunState.value == DpiAutoRunState.RUNNING ||
            autoRunState.value == DpiAutoRunState.CANCELLING ||
            autoRunState.value == DpiAutoRunState.APPLYING
        ) return
        val selected = decodeGroupIds(selectedAutoGroups.value)
        if (selected.isEmpty()) return
        val targets = DpiDomainCatalog.default
            .filter { it.id in selected }
            .flatMap { it.targets }
            .distinctBy { it.id }
        if (targets.isEmpty()) return

        val generation = autoGeneration.incrementAndGet()
        val invalidated = AtomicBoolean(false)
        autoReport.value = null
        autoRunState.value = DpiAutoRunState.RUNNING
        viewModelScope.launch {
            try {
                val report = runAutoSearch(targets) {
                    val shouldCancel = autoGeneration.get() != generation || vpnState.value != VpnState.Idle
                    if (shouldCancel) invalidated.set(true)
                    shouldCancel
                }
                if (invalidated.get() || autoGeneration.get() != generation) {
                    if (
                        autoRunState.value == DpiAutoRunState.RUNNING ||
                        autoRunState.value == DpiAutoRunState.CANCELLING
                    ) {
                        autoRunState.value = DpiAutoRunState.IDLE
                    }
                    return@launch
                }
                if (vpnState.value != VpnState.Idle) {
                    autoRunState.value = DpiAutoRunState.IDLE
                    return@launch
                }
                autoReport.value = report
                autoRunState.value = DpiAutoRunState.COMPLETE
            } catch (cancelled: CancellationException) {
                if (
                    autoRunState.value == DpiAutoRunState.RUNNING ||
                    autoRunState.value == DpiAutoRunState.CANCELLING
                ) {
                    autoRunState.value = DpiAutoRunState.IDLE
                }
                throw cancelled
            } catch (_: Exception) {
                if (autoGeneration.get() == generation) autoRunState.value = DpiAutoRunState.ERROR
                else if (autoRunState.value == DpiAutoRunState.CANCELLING) {
                    autoRunState.value = DpiAutoRunState.IDLE
                }
            }
        }
    }

    fun cancelAutoTest() = cancelAutoTest(resetReport = false)

    fun applyAuto() {
        val winner = autoReport.value?.winner ?: return
        if (autoRunState.value != DpiAutoRunState.COMPLETE) return
        if (settings.value?.preset == DpiPreset.AUTO &&
            settings.value?.dpiAutoCandidateId == winner.candidate.id
        ) return

        autoRunState.value = DpiAutoRunState.APPLYING
        presetOverride.value = DpiPreset.AUTO
        viewModelScope.launch {
            writeMutex.withLock {
                try {
                    // Candidate ID is validated by RoutesStore before AUTO is activated.
                    // Write in this order so process death cannot expose an unbound AUTO preset.
                    setAutoCandidateId(winner.candidate.id)
                    setPreset(DpiPreset.AUTO)
                    if (
                        settings.value?.preset != DpiPreset.AUTO ||
                        settings.value?.dpiAutoCandidateId != winner.candidate.id
                    ) {
                        settings.first {
                            it?.preset == DpiPreset.AUTO &&
                                it.dpiAutoCandidateId == winner.candidate.id
                        }
                    }
                    restartTunnel()
                    savedStateHandle[KEY_EDITING_AUTO] = null
                    if (presetOverride.value == DpiPreset.AUTO) presetOverride.value = null
                    autoRunState.value = DpiAutoRunState.COMPLETE
                    _autoApplied.emit(Unit)
                } catch (cancelled: CancellationException) {
                    if (presetOverride.value == DpiPreset.AUTO) presetOverride.value = null
                    autoRunState.value = DpiAutoRunState.COMPLETE
                    throw cancelled
                } catch (_: Exception) {
                    if (presetOverride.value == DpiPreset.AUTO) presetOverride.value = null
                    autoRunState.value = DpiAutoRunState.ERROR
                }
            }
        }
    }

    fun chooseRecommended() {
        if (saveState.value == DpiSaveState.SAVING) return
        cancelAutoTest(resetReport = false)
        saveState.value = DpiSaveState.IDLE
        savedStateHandle[KEY_EDITING_CUSTOM] = null
        savedStateHandle[KEY_EDITING_AUTO] = null

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

        cancelAutoTest(resetReport = false)
        savedStateHandle[KEY_EDITING_AUTO] = false
        savedStateHandle[KEY_EDITING_CUSTOM] = true
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
                        savedStateHandle[KEY_CUSTOM_DRAFT] = null
                        if (editingOverride.value == true) savedStateHandle[KEY_EDITING_CUSTOM] = null
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

    private fun cancelAutoTest(resetReport: Boolean) {
        if (autoRunState.value == DpiAutoRunState.RUNNING) {
            autoGeneration.incrementAndGet()
            autoRunState.value = DpiAutoRunState.CANCELLING
        }
        if (resetReport) autoReport.value = null
    }

    override fun onCleared() {
        autoGeneration.incrementAndGet()
        super.onCleared()
    }

    companion object {
        private const val KEY_CUSTOM_DRAFT = "dpi_custom_draft"
        private const val KEY_EDITING_CUSTOM = "dpi_editing_custom"
        private const val KEY_EDITING_AUTO = "dpi_editing_auto"
        private const val KEY_AUTO_GROUPS = "dpi_auto_groups"

        internal fun decodeGroupIds(csv: String): Set<String> {
            val allowed = DpiDomainCatalog.default.map { it.id }.toSet()
            return csv.split(',').map { it.trim() }.filter { it in allowed }.toSet()
        }

        fun factory(
            store: RoutesStore,
            vpnState: StateFlow<VpnState>,
            runAutoSearch: suspend (List<DpiProbeTarget>, () -> Boolean) -> DpiAutoSearchReport,
            restartTunnel: () -> Unit,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DpiViewModel(
                    settings = store.settings,
                    setPreset = store::setPreset,
                    setCustomArgs = store::setCustomArgs,
                    setAutoCandidateId = store::setAutoCandidateId,
                    vpnState = vpnState,
                    runAutoSearch = runAutoSearch,
                    restartTunnel = restartTunnel,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
