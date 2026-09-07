package dev.detour.app.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.detour.app.core.DpiArgs
import dev.detour.app.core.DpiPreset
import dev.detour.app.core.DpiProxyTestCatalog
import dev.detour.app.core.DpiProxyTestConfig
import dev.detour.app.core.DpiProxyTestProgress
import dev.detour.app.core.DpiProxyTestRanker
import dev.detour.app.core.DpiProxyTestRun
import dev.detour.app.core.DpiProxyTestStrategySelection
import dev.detour.app.core.DpiProxyTester
import dev.detour.app.core.toSummary
import dev.detour.app.data.DpiProxyTestHistoryStore
import dev.detour.app.data.RoutesStore
import dev.detour.app.data.TriSettings
import dev.detour.app.vpn.VpnController
import dev.detour.app.vpn.VpnState
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

enum class DpiProxyTestError { VPN_ACTIVE, FAILED, HISTORY_SAVE }

data class DpiProxyTestUiState(
    val selectedDomainIds: Set<String> = DpiProxyTestCatalog.defaultSelectedIds,
    val selectedReferenceStrategyIds: Set<String> = DpiProxyTestStrategySelection.defaultReferenceIds,
    val customStrategyDraft: String = "",
    val attemptsPerHost: Int = DpiProxyTestConfig.DEFAULT_ATTEMPTS,
    val concurrency: Int = DpiProxyTestConfig.DEFAULT_CONCURRENCY,
    val timeoutSeconds: Int = DpiProxyTestConfig.DEFAULT_TIMEOUT_SECONDS,
    val running: Boolean = false,
    val cancelled: Boolean = false,
    val progress: DpiProxyTestProgress? = null,
    val historyLoaded: Boolean = false,
    val history: List<DpiProxyTestRun> = emptyList(),
    val selectedRunId: String? = null,
    val error: DpiProxyTestError? = null,
    val applyingStrategyId: String? = null,
    val appliedStrategyId: String? = null,
    val applyErrorStrategyId: String? = null,
) {
    val selectedHostCount: Int
        get() = DpiProxyTestCatalog.selectedHosts(selectedDomainIds).size

    val customStrategy
        get() = DpiProxyTestStrategySelection.custom(customStrategyDraft)

    val customStrategyInvalid: Boolean
        get() = customStrategyDraft.isNotBlank() && customStrategy == null

    val selectedStrategyCount: Int
        get() = selectedReferenceStrategyIds.size + if (customStrategy != null) 1 else 0

    val selectedRun: DpiProxyTestRun?
        get() = history.firstOrNull { it.id == selectedRunId }

    val results
        get() = selectedRun?.results.orEmpty()

    val completed: Boolean
        get() = selectedRun != null

    val canStart: Boolean
        get() = historyLoaded &&
            !running &&
            applyingStrategyId == null &&
            selectedHostCount > 0 &&
            selectedStrategyCount > 0 &&
            !customStrategyInvalid
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
    private val savedStateHandle: SavedStateHandle,
    private val proxyHistoryStore: DpiProxyTestHistoryStore,
) : ViewModel() {
    private val customDraft = savedStateHandle.getStateFlow<String?>(KEY_CUSTOM_DRAFT, null)
    private val editingOverride = savedStateHandle.getStateFlow<Boolean?>(KEY_EDITING_CUSTOM, null)
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
        initialValue = dpiUiState(settings.value, customDraft.value, editingOverride.value),
    )

    private val _proxyTestState = MutableStateFlow(DpiProxyTestUiState())
    val proxyTestState: StateFlow<DpiProxyTestUiState> = _proxyTestState
    private val _proxyTestOpen = MutableStateFlow(false)
    val proxyTestOpen: StateFlow<Boolean> = _proxyTestOpen
    private var proxyTestJob: Job? = null
    private val proxyGeneration = AtomicInteger(0)
    private val proxyStopGeneration = AtomicInteger(-1)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val history = proxyHistoryStore.load()
            _proxyTestState.update { state ->
                state.copy(
                    historyLoaded = true,
                    history = history,
                    selectedRunId = state.selectedRunId ?: history.firstOrNull()?.id,
                )
            }
        }
    }

    fun editCustom() {
        savedStateHandle[KEY_EDITING_CUSTOM] = true
    }

    fun setCustomField(value: String) {
        savedStateHandle[KEY_CUSTOM_DRAFT] = value.replace("\r", " ").replace("\n", " ")
        if (saveState.value == DpiSaveState.ERROR) saveState.value = DpiSaveState.IDLE
    }

    fun chooseRecommended() {
        if (saveState.value == DpiSaveState.SAVING) return
        saveState.value = DpiSaveState.IDLE
        savedStateHandle[KEY_EDITING_CUSTOM] = null

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

        savedStateHandle[KEY_EDITING_CUSTOM] = true
        presetOverride.value = DpiPreset.CUSTOM
        saveState.value = DpiSaveState.SAVING
        viewModelScope.launch {
            writeMutex.withLock {
                try {
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

    fun openProxyTest() {
        _proxyTestOpen.value = true
    }

    fun closeProxyTest() {
        stopProxyTest()
        _proxyTestOpen.value = false
    }

    fun toggleProxyDomain(id: String) {
        val available = DpiProxyTestCatalog.domainLists.any { it.id == id }
        if (!available || _proxyTestState.value.running) return
        _proxyTestState.update { state ->
            val next = state.selectedDomainIds.toMutableSet().apply {
                if (!add(id)) remove(id)
            }.toSet()
            state.copy(
                selectedDomainIds = next,
                cancelled = false,
                progress = null,
                error = null,
            )
        }
    }

    fun toggleProxyStrategy(id: String) {
        val available = DpiProxyTestCatalog.strategies.any { it.id == id }
        if (!available || _proxyTestState.value.running) return
        _proxyTestState.update { state ->
            val next = state.selectedReferenceStrategyIds.toMutableSet().apply {
                if (!add(id)) remove(id)
            }.toSet()
            state.copy(
                selectedReferenceStrategyIds = next,
                cancelled = false,
                progress = null,
                error = null,
            )
        }
    }

    fun selectAllProxyStrategies() {
        if (_proxyTestState.value.running) return
        _proxyTestState.update {
            it.copy(
                selectedReferenceStrategyIds = DpiProxyTestStrategySelection.defaultReferenceIds,
                error = null,
            )
        }
    }

    fun clearProxyStrategies() {
        if (_proxyTestState.value.running) return
        _proxyTestState.update {
            it.copy(
                selectedReferenceStrategyIds = emptySet(),
                error = null,
            )
        }
    }

    fun setProxyCustomStrategy(value: String) {
        if (_proxyTestState.value.running) return
        _proxyTestState.update {
            it.copy(
                customStrategyDraft = value.replace("\r", " ").replace("\n", " "),
                cancelled = false,
                progress = null,
                error = null,
            )
        }
    }

    fun selectProxyRun(id: String) {
        val state = _proxyTestState.value
        if (state.running || state.applyingStrategyId != null || state.history.none { it.id == id }) return
        _proxyTestState.update {
            it.copy(
                selectedRunId = id,
                appliedStrategyId = null,
                applyErrorStrategyId = null,
            )
        }
    }

    fun setProxyAttempts(value: Int) {
        if (_proxyTestState.value.running) return
        _proxyTestState.update {
            it.copy(
                attemptsPerHost = value.coerceIn(DpiProxyTestConfig.ATTEMPTS_RANGE),
                cancelled = false,
                progress = null,
                error = null,
            )
        }
    }

    fun setProxyConcurrency(value: Int) {
        if (_proxyTestState.value.running) return
        _proxyTestState.update {
            it.copy(
                concurrency = value.coerceIn(DpiProxyTestConfig.CONCURRENCY_RANGE),
                cancelled = false,
                progress = null,
                error = null,
            )
        }
    }

    fun setProxyTimeoutSeconds(value: Int) {
        if (_proxyTestState.value.running) return
        _proxyTestState.update {
            it.copy(
                timeoutSeconds = value.coerceIn(DpiProxyTestConfig.TIMEOUT_RANGE),
                cancelled = false,
                progress = null,
                error = null,
            )
        }
    }

    fun startProxyTest(context: Context) {
        val snapshot = _proxyTestState.value
        if (!snapshot.canStart || proxyTestJob?.isActive == true) return
        if (VpnController.state.value == VpnState.Active || VpnController.state.value == VpnState.Starting) {
            _proxyTestState.update { it.copy(error = DpiProxyTestError.VPN_ACTIVE) }
            return
        }

        val strategies = DpiProxyTestStrategySelection.build(
            referenceIds = snapshot.selectedReferenceStrategyIds,
            customRaw = snapshot.customStrategyDraft,
        )
        if (strategies.isEmpty()) return

        val generation = proxyGeneration.incrementAndGet()
        proxyStopGeneration.set(-1)
        val selectedIds = snapshot.selectedDomainIds
        val config = DpiProxyTestConfig(
            attemptsPerHost = snapshot.attemptsPerHost,
            concurrency = snapshot.concurrency,
            timeoutSeconds = snapshot.timeoutSeconds,
        )
        val appContext = context.applicationContext
        _proxyTestState.update {
            it.copy(
                running = true,
                cancelled = false,
                progress = null,
                error = null,
                applyingStrategyId = null,
                appliedStrategyId = null,
                applyErrorStrategyId = null,
            )
        }

        proxyTestJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = DpiProxyTester(appContext).run(
                    selectedIds = selectedIds,
                    config = config,
                    strategies = strategies,
                    onProgress = { progress ->
                        if (proxyGeneration.get() == generation) {
                            _proxyTestState.update { it.copy(progress = progress) }
                        }
                    },
                )
                if (proxyGeneration.get() != generation) return@launch

                val now = System.currentTimeMillis()
                val run = DpiProxyTestRun(
                    id = "$now-${UUID.randomUUID()}",
                    createdAtEpochMs = now,
                    selectedDomainIds = selectedIds,
                    config = config,
                    results = DpiProxyTestRanker.rank(results).map { it.toSummary() },
                )
                val updatedHistory = (listOf(run) + _proxyTestState.value.history)
                    .distinctBy { it.id }
                    .take(DpiProxyTestHistoryStore.MAX_RUNS)

                _proxyTestState.update {
                    it.copy(
                        running = false,
                        cancelled = false,
                        progress = null,
                        history = updatedHistory,
                        selectedRunId = run.id,
                        error = null,
                    )
                }

                try {
                    proxyHistoryStore.save(updatedHistory)
                } catch (_: Exception) {
                    if (proxyGeneration.get() == generation) {
                        _proxyTestState.update { it.copy(error = DpiProxyTestError.HISTORY_SAVE) }
                    }
                }
            } catch (cancelled: CancellationException) {
                if (proxyGeneration.get() != generation) return@launch
                val userStopped = proxyStopGeneration.get() == generation
                _proxyTestState.update {
                    it.copy(
                        running = false,
                        progress = null,
                        cancelled = userStopped,
                        error = if (userStopped) null else DpiProxyTestError.VPN_ACTIVE,
                    )
                }
                if (!userStopped) return@launch
            } catch (error: Exception) {
                if (proxyGeneration.get() != generation) return@launch
                val vpnContaminated = error.message?.contains("system VPN active", ignoreCase = true) == true
                _proxyTestState.update {
                    it.copy(
                        running = false,
                        progress = null,
                        cancelled = false,
                        error = if (vpnContaminated) DpiProxyTestError.VPN_ACTIVE else DpiProxyTestError.FAILED,
                    )
                }
            } finally {
                if (proxyGeneration.get() == generation) proxyTestJob = null
            }
        }
    }

    fun stopProxyTest() {
        val state = _proxyTestState.value
        val job = proxyTestJob ?: return
        if (!state.running || !job.isActive) return
        proxyStopGeneration.set(proxyGeneration.get())
        job.cancel(CancellationException("proxy test stopped by user"))
    }

    fun applyProxyStrategy(strategyId: String) {
        val state = _proxyTestState.value
        if (state.running || state.applyingStrategyId != null) return
        val result = state.results.firstOrNull { it.strategy.id == strategyId } ?: return
        if (!result.backendStarted || !result.completed) return
        val strategy = result.strategy
        if (!DpiArgs.isValid(strategy.command)) return

        _proxyTestState.update {
            it.copy(
                applyingStrategyId = strategyId,
                applyErrorStrategyId = null,
            )
        }
        presetOverride.value = DpiPreset.CUSTOM

        viewModelScope.launch {
            writeMutex.withLock {
                try {
                    setCustomArgs(strategy.command)
                    setPreset(DpiPreset.CUSTOM)
                    if (
                        settings.value?.preset != DpiPreset.CUSTOM ||
                        settings.value?.dpiCustomArgs?.trim() != strategy.command.trim()
                    ) {
                        settings.first {
                            it?.preset == DpiPreset.CUSTOM &&
                                it.dpiCustomArgs.trim() == strategy.command.trim()
                        }
                    }
                    restartTunnel()
                    savedStateHandle[KEY_CUSTOM_DRAFT] = null
                    savedStateHandle[KEY_EDITING_CUSTOM] = null
                    if (presetOverride.value == DpiPreset.CUSTOM) presetOverride.value = null
                    _proxyTestState.update {
                        it.copy(
                            applyingStrategyId = null,
                            appliedStrategyId = strategyId,
                            applyErrorStrategyId = null,
                        )
                    }
                    _customSaved.emit(Unit)
                } catch (cancelled: CancellationException) {
                    if (presetOverride.value == DpiPreset.CUSTOM) presetOverride.value = null
                    _proxyTestState.update { it.copy(applyingStrategyId = null) }
                    throw cancelled
                } catch (_: Exception) {
                    if (presetOverride.value == DpiPreset.CUSTOM) presetOverride.value = null
                    _proxyTestState.update {
                        it.copy(
                            applyingStrategyId = null,
                            applyErrorStrategyId = strategyId,
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val KEY_CUSTOM_DRAFT = "dpi_custom_draft"
        private const val KEY_EDITING_CUSTOM = "dpi_editing_custom"

        fun factory(
            store: RoutesStore,
            appContext: Context,
            restartTunnel: () -> Unit,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DpiViewModel(
                    settings = store.settings,
                    setPreset = store::setPreset,
                    setCustomArgs = store::setCustomArgs,
                    restartTunnel = restartTunnel,
                    savedStateHandle = createSavedStateHandle(),
                    proxyHistoryStore = DpiProxyTestHistoryStore(appContext),
                )
            }
        }
    }
}
