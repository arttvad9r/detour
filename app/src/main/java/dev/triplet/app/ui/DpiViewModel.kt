package dev.triplet.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.triplet.app.core.DpiArgs
import dev.triplet.app.core.DpiAutoDomainCatalog
import dev.triplet.app.core.DpiAutoDomainPlan
import dev.triplet.app.core.DpiAutoProgress
import dev.triplet.app.core.DpiAutoSearchReport
import dev.triplet.app.core.DpiAutoTestOptions
import dev.triplet.app.core.DpiDomainInput
import dev.triplet.app.core.DpiPerDomainPlan
import dev.triplet.app.core.DpiPerDomainPlanner
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
    val selectedAutoGroups: Set<String> = DpiAutoDomainCatalog.default.map { it.id }.toSet(),
    val customAutoDomains: String = "",
    val customAutoDomainsInvalid: Boolean = false,
    val autoAttempts: Int = DpiAutoTestOptions.DEFAULT_ATTEMPTS,
    val autoRunState: DpiAutoRunState = DpiAutoRunState.IDLE,
    val autoProgress: DpiAutoProgress? = null,
    val autoReport: DpiAutoSearchReport? = null,
    val autoDomainPlan: DpiPerDomainPlan? = null,
    val vpnIdle: Boolean = true,
    val appliedAutoCandidateId: String = "",
    val appliedAutoDomainPlan: DpiAutoDomainPlan? = null,
) {
    val canSaveCustom: Boolean
        get() = saveState != DpiSaveState.SAVING &&
            customField.isNotBlank() && !customInvalid && customChanged

    val canRunAuto: Boolean
        get() = vpnIdle &&
            (selectedAutoGroups.isNotEmpty() || customAutoDomains.isNotBlank()) &&
            !customAutoDomainsInvalid &&
            DpiAutoTestOptions.isValidAttempts(autoAttempts) &&
            autoRunState != DpiAutoRunState.RUNNING &&
            autoRunState != DpiAutoRunState.CANCELLING &&
            autoRunState != DpiAutoRunState.APPLYING

    private val generatedAutoDomainPlan: DpiAutoDomainPlan?
        get() {
            val plan = autoDomainPlan ?: return null
            if (!plan.complete || plan.assignments.isEmpty()) return null
            return runCatching { DpiAutoDomainPlan.fromPlan(plan) }.getOrNull()
        }

    val autoPlanApplied: Boolean
        get() {
            val generated = generatedAutoDomainPlan ?: return false
            return preset == DpiPreset.AUTO && appliedAutoDomainPlan == generated
        }

    val canApplyAuto: Boolean
        get() = autoRunState == DpiAutoRunState.COMPLETE &&
            generatedAutoDomainPlan != null && !autoPlanApplied
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
        appliedAutoDomainPlan = settings?.dpiAutoDomainPlan,
    )
}

private data class DpiAutoTargetsDraft(
    val selectedGroups: Set<String>,
    val customDomains: String,
    val customDomainsInvalid: Boolean,
    val attempts: Int,
)

private data class DpiAutoResult(
    val report: DpiAutoSearchReport?,
    val plan: DpiPerDomainPlan?,
    val progress: DpiAutoProgress?,
)

private data class DpiAutoPresentation(
    val editingOverride: Boolean?,
    val targetsDraft: DpiAutoTargetsDraft,
    val runState: DpiAutoRunState,
    val result: DpiAutoResult,
    val vpnState: VpnState,
)

class DpiViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val setPreset: suspend (DpiPreset) -> Unit,
    private val setCustomArgs: suspend (String) -> Unit,
    private val setAutoDomainPlan: suspend (DpiAutoDomainPlan) -> Unit,
    private val vpnState: StateFlow<VpnState>,
    private val runAutoSearch: suspend (
        List<DpiProbeTarget>,
        Int,
        () -> Boolean,
        (DpiAutoProgress) -> Unit,
    ) -> DpiAutoSearchReport,
    private val restartTunnel: () -> Unit,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    constructor(
        settings: StateFlow<TriSettings?>,
        setPreset: suspend (DpiPreset) -> Unit,
        setCustomArgs: suspend (String) -> Unit,
        setAutoDomainPlan: suspend (DpiAutoDomainPlan) -> Unit,
        vpnState: StateFlow<VpnState>,
        runAutoSearch: suspend (
            List<DpiProbeTarget>,
            () -> Boolean,
            (DpiAutoProgress) -> Unit,
        ) -> DpiAutoSearchReport,
        restartTunnel: () -> Unit,
        savedStateHandle: SavedStateHandle,
    ) : this(
        settings = settings,
        setPreset = setPreset,
        setCustomArgs = setCustomArgs,
        setAutoDomainPlan = setAutoDomainPlan,
        vpnState = vpnState,
        runAutoSearch = { targets, _, cancelled, progress ->
            runAutoSearch(targets, cancelled, progress)
        },
        restartTunnel = restartTunnel,
        savedStateHandle = savedStateHandle,
    )

    constructor(
        settings: StateFlow<TriSettings?>,
        setPreset: suspend (DpiPreset) -> Unit,
        setCustomArgs: suspend (String) -> Unit,
        setAutoDomainPlan: suspend (DpiAutoDomainPlan) -> Unit,
        vpnState: StateFlow<VpnState>,
        runAutoSearch: suspend (List<DpiProbeTarget>, () -> Boolean) -> DpiAutoSearchReport,
        restartTunnel: () -> Unit,
        savedStateHandle: SavedStateHandle,
    ) : this(
        settings = settings,
        setPreset = setPreset,
        setCustomArgs = setCustomArgs,
        setAutoDomainPlan = setAutoDomainPlan,
        vpnState = vpnState,
        runAutoSearch = { targets, _, cancelled, _ -> runAutoSearch(targets, cancelled) },
        restartTunnel = restartTunnel,
        savedStateHandle = savedStateHandle,
    )

    private val customDraft = savedStateHandle.getStateFlow<String?>(KEY_CUSTOM_DRAFT, null)
    private val editingOverride = savedStateHandle.getStateFlow<Boolean?>(KEY_EDITING_CUSTOM, null)
    private val editingAutoOverride = savedStateHandle.getStateFlow<Boolean?>(KEY_EDITING_AUTO, null)
    private val selectedAutoGroups = savedStateHandle.getStateFlow(
        KEY_AUTO_GROUPS,
        DpiAutoDomainCatalog.default.joinToString(",") { it.id },
    )
    private val customAutoDomains = savedStateHandle.getStateFlow(KEY_AUTO_CUSTOM_DOMAINS, "")
    private val autoAttempts = savedStateHandle.getStateFlow(
        KEY_AUTO_ATTEMPTS,
        DpiAutoTestOptions.DEFAULT_ATTEMPTS,
    )
    private val presetOverride = MutableStateFlow<DpiPreset?>(null)
    private val saveState = MutableStateFlow(DpiSaveState.IDLE)
    private val autoRunState = MutableStateFlow(DpiAutoRunState.IDLE)
    private val autoProgress = MutableStateFlow<DpiAutoProgress?>(null)
    private val autoReport = MutableStateFlow<DpiAutoSearchReport?>(null)
    private val autoDomainPlan = MutableStateFlow<DpiPerDomainPlan?>(null)
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

    private val autoTargetsDraft = combine(
        selectedAutoGroups,
        customAutoDomains,
        autoAttempts,
    ) { groupCsv, customDomains, attempts ->
        val parsed = DpiDomainInput.parse(customDomains)
        DpiAutoTargetsDraft(
            selectedGroups = decodeGroupIds(groupCsv),
            customDomains = customDomains,
            customDomainsInvalid = !parsed.isValid,
            attempts = attempts,
        )
    }

    private val autoResult = combine(autoReport, autoDomainPlan, autoProgress) { report, plan, progress ->
        DpiAutoResult(report = report, plan = plan, progress = progress)
    }

    private val autoPresentation = combine(
        editingAutoOverride,
        autoTargetsDraft,
        autoRunState,
        autoResult,
        vpnState,
    ) { editingAuto, targetsDraft, runState, result, currentVpnState ->
        DpiAutoPresentation(
            editingOverride = editingAuto,
            targetsDraft = targetsDraft,
            runState = runState,
            result = result,
            vpnState = currentVpnState,
        )
    }

    val uiState: StateFlow<DpiUiState> = combine(baseUiState, autoPresentation) { base, auto ->
        val editingAuto = auto.editingOverride ?: (base.preset == DpiPreset.AUTO)
        base.copy(
            editingCustom = if (editingAuto) false else base.editingCustom,
            editingAuto = editingAuto,
            selectedAutoGroups = auto.targetsDraft.selectedGroups,
            customAutoDomains = auto.targetsDraft.customDomains,
            customAutoDomainsInvalid = auto.targetsDraft.customDomainsInvalid,
            autoAttempts = auto.targetsDraft.attempts,
            autoRunState = auto.runState,
            autoProgress = auto.result.progress,
            autoReport = auto.result.report,
            autoDomainPlan = auto.result.plan,
            vpnIdle = auto.vpnState == VpnState.Idle,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = dpiUiState(settings.value, customDraft.value, editingOverride.value).copy(
            editingAuto = editingAutoOverride.value ?: (settings.value?.preset == DpiPreset.AUTO),
            selectedAutoGroups = decodeGroupIds(selectedAutoGroups.value),
            customAutoDomains = customAutoDomains.value,
            customAutoDomainsInvalid = !DpiDomainInput.parse(customAutoDomains.value).isValid,
            autoAttempts = autoAttempts.value,
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

    fun setAutoCustomDomains(value: String) {
        if (!autoControlsMutable()) return
        savedStateHandle[KEY_AUTO_CUSTOM_DOMAINS] = value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .take(MAX_CUSTOM_DOMAIN_DRAFT_CHARS)
        invalidateAutoResult()
    }

    fun setAutoAttempts(value: Int) {
        if (!autoControlsMutable() || !DpiAutoTestOptions.isValidAttempts(value)) return
        if (value == autoAttempts.value) return
        savedStateHandle[KEY_AUTO_ATTEMPTS] = value
        invalidateAutoResult()
    }

    fun toggleAutoGroup(id: String) {
        if (!autoControlsMutable()) return
        if (DpiAutoDomainCatalog.default.none { it.id == id }) return
        val next = decodeGroupIds(selectedAutoGroups.value).toMutableSet()
        if (!next.add(id)) next.remove(id)
        savedStateHandle[KEY_AUTO_GROUPS] = DpiAutoDomainCatalog.default
            .map { it.id }
            .filter { it in next }
            .joinToString(",")
        invalidateAutoResult()
    }

    fun startAutoTest() {
        if (
            vpnState.value != VpnState.Idle ||
            autoRunState.value == DpiAutoRunState.RUNNING ||
            autoRunState.value == DpiAutoRunState.CANCELLING ||
            autoRunState.value == DpiAutoRunState.APPLYING
        ) return
        val selected = decodeGroupIds(selectedAutoGroups.value)
        val customParsed = DpiDomainInput.parse(customAutoDomains.value)
        if (!customParsed.isValid) return
        val attempts = autoAttempts.value
        if (!DpiAutoTestOptions.isValidAttempts(attempts)) return
        val targets = (
            DpiAutoDomainCatalog.default
                .filter { it.id in selected }
                .flatMap { it.targets } + customParsed.targets
            ).distinctBy { it.host }
        if (targets.isEmpty()) return

        val generation = autoGeneration.incrementAndGet()
        val invalidated = AtomicBoolean(false)
        autoProgress.value = null
        autoReport.value = null
        autoDomainPlan.value = null
        autoRunState.value = DpiAutoRunState.RUNNING
        viewModelScope.launch {
            try {
                val report = runAutoSearch(
                    targets,
                    attempts,
                    {
                        val shouldCancel = autoGeneration.get() != generation || vpnState.value != VpnState.Idle
                        if (shouldCancel) invalidated.set(true)
                        shouldCancel
                    },
                    { progress ->
                        if (
                            autoGeneration.get() == generation &&
                            vpnState.value == VpnState.Idle &&
                            autoRunState.value == DpiAutoRunState.RUNNING
                        ) {
                            autoProgress.value = progress
                        }
                    },
                )
                if (invalidated.get() || autoGeneration.get() != generation) {
                    autoProgress.value = null
                    if (
                        autoRunState.value == DpiAutoRunState.RUNNING ||
                        autoRunState.value == DpiAutoRunState.CANCELLING
                    ) {
                        autoRunState.value = DpiAutoRunState.IDLE
                    }
                    return@launch
                }
                if (vpnState.value != VpnState.Idle) {
                    autoProgress.value = null
                    autoRunState.value = DpiAutoRunState.IDLE
                    return@launch
                }

                val plan = DpiPerDomainPlanner.fromReport(report)
                // Validate the persisted representation and compiler before exposing Apply.
                if (plan.complete && plan.assignments.isNotEmpty()) {
                    DpiAutoDomainPlan.fromPlan(plan).compileArgs()
                }
                autoProgress.value = null
                autoReport.value = report
                autoDomainPlan.value = plan
                autoRunState.value = DpiAutoRunState.COMPLETE
            } catch (cancelled: CancellationException) {
                autoProgress.value = null
                if (
                    autoRunState.value == DpiAutoRunState.RUNNING ||
                    autoRunState.value == DpiAutoRunState.CANCELLING
                ) {
                    autoRunState.value = DpiAutoRunState.IDLE
                }
                throw cancelled
            } catch (_: Exception) {
                autoProgress.value = null
                if (autoGeneration.get() == generation) autoRunState.value = DpiAutoRunState.ERROR
                else if (autoRunState.value == DpiAutoRunState.CANCELLING) {
                    autoRunState.value = DpiAutoRunState.IDLE
                }
            }
        }
    }

    fun cancelAutoTest() = cancelAutoTest(resetReport = false)

    fun applyAuto() {
        val plan = autoDomainPlan.value ?: return
        if (autoRunState.value != DpiAutoRunState.COMPLETE || !plan.complete || plan.assignments.isEmpty()) return
        val storedPlan = runCatching {
            DpiAutoDomainPlan.fromPlan(plan).also { it.compileArgs() }
        }.getOrNull() ?: return
        if (
            settings.value?.preset == DpiPreset.AUTO &&
            settings.value?.dpiAutoDomainPlan == storedPlan
        ) return

        autoRunState.value = DpiAutoRunState.APPLYING
        presetOverride.value = DpiPreset.AUTO
        viewModelScope.launch {
            writeMutex.withLock {
                try {
                    // The plan contains only trusted candidate IDs and is compiler-validated.
                    // Persist it before AUTO so process death cannot expose an unbound preset.
                    setAutoDomainPlan(storedPlan)
                    setPreset(DpiPreset.AUTO)
                    if (
                        settings.value?.preset != DpiPreset.AUTO ||
                        settings.value?.dpiAutoDomainPlan != storedPlan ||
                        settings.value?.dpiAutoCandidateId?.isNotBlank() == true
                    ) {
                        settings.first {
                            it?.preset == DpiPreset.AUTO &&
                                it.dpiAutoDomainPlan == storedPlan &&
                                it.dpiAutoCandidateId.isBlank()
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

    private fun autoControlsMutable(): Boolean =
        autoRunState.value != DpiAutoRunState.RUNNING &&
            autoRunState.value != DpiAutoRunState.CANCELLING &&
            autoRunState.value != DpiAutoRunState.APPLYING

    private fun invalidateAutoResult() {
        autoProgress.value = null
        autoReport.value = null
        autoDomainPlan.value = null
        if (autoRunState.value != DpiAutoRunState.IDLE) autoRunState.value = DpiAutoRunState.IDLE
    }

    private fun cancelAutoTest(resetReport: Boolean) {
        if (autoRunState.value == DpiAutoRunState.RUNNING) {
            autoGeneration.incrementAndGet()
            autoRunState.value = DpiAutoRunState.CANCELLING
        }
        if (resetReport) {
            autoProgress.value = null
            autoReport.value = null
            autoDomainPlan.value = null
        }
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
        private const val KEY_AUTO_CUSTOM_DOMAINS = "dpi_auto_custom_domains"
        private const val KEY_AUTO_ATTEMPTS = "dpi_auto_attempts"
        private const val MAX_CUSTOM_DOMAIN_DRAFT_CHARS = 8192

        internal fun decodeGroupIds(csv: String): Set<String> {
            val allowed = DpiAutoDomainCatalog.default.map { it.id }.toSet()
            return csv.split(',').map { it.trim() }.filter { it in allowed }.toSet()
        }

        fun factory(
            store: RoutesStore,
            vpnState: StateFlow<VpnState>,
            runAutoSearch: suspend (
                List<DpiProbeTarget>,
                Int,
                () -> Boolean,
                (DpiAutoProgress) -> Unit,
            ) -> DpiAutoSearchReport,
            restartTunnel: () -> Unit,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DpiViewModel(
                    settings = store.settings,
                    setPreset = store::setPreset,
                    setCustomArgs = store::setCustomArgs,
                    setAutoDomainPlan = store::setAutoDomainPlan,
                    vpnState = vpnState,
                    runAutoSearch = runAutoSearch,
                    restartTunnel = restartTunnel,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }

        fun factory(
            store: RoutesStore,
            vpnState: StateFlow<VpnState>,
            runAutoSearch: suspend (
                List<DpiProbeTarget>,
                () -> Boolean,
                (DpiAutoProgress) -> Unit,
            ) -> DpiAutoSearchReport,
            restartTunnel: () -> Unit,
        ): ViewModelProvider.Factory = factory(
            store = store,
            vpnState = vpnState,
            runAutoSearch = { targets, _, cancelled, progress ->
                runAutoSearch(targets, cancelled, progress)
            },
            restartTunnel = restartTunnel,
        )

        fun factory(
            store: RoutesStore,
            vpnState: StateFlow<VpnState>,
            runAutoSearch: suspend (List<DpiProbeTarget>, () -> Boolean) -> DpiAutoSearchReport,
            restartTunnel: () -> Unit,
        ): ViewModelProvider.Factory = factory(
            store = store,
            vpnState = vpnState,
            runAutoSearch = { targets, _, cancelled, _ -> runAutoSearch(targets, cancelled) },
            restartTunnel = restartTunnel,
        )
    }
}
