package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.DnsOptions
import dev.triplet.app.core.MultiHopEntryRef
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import dev.triplet.app.vpn.EffectiveRoutes
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import dev.triplet.app.vpn.resolveMultiHopEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class DiagnosticProbeState { NOT_RUN, RUNNING, PASS, FAIL, NOT_APPLICABLE }

internal data class DiagnosticsUiState(
    val loading: Boolean = true,
    val checking: Boolean = false,
    val vpnPermissionGranted: Boolean = false,
    val vpnState: VpnState = VpnState.Idle,
    val engineReady: Boolean = false,
    val vpnProbe: DiagnosticProbeState = DiagnosticProbeState.NOT_RUN,
    val dpiProbe: DiagnosticProbeState = DiagnosticProbeState.NOT_RUN,
    val vpnLatencyMs: Long? = null,
    val profileKind: VpnProfileKind = VpnProfileKind.VLESS,
    val profileName: String? = null,
    val serverLabel: String? = null,
    val endpointCount: Int = 0,
    val multiHopEnabled: Boolean = false,
    val multiHopEntryLabel: String? = null,
    val multiHopValid: Boolean = true,
    val dnsId: String = "google",
    val dnsValid: Boolean = true,
    val vpnRouteCount: Int = 0,
    val dpiRouteCount: Int = 0,
    val lastError: String? = null,
    val checkedAt: Long? = null,
)

internal class DiagnosticsViewModel(
    private val loadSettings: suspend () -> TriSettings,
    private val vpnState: StateFlow<VpnState>,
    private val resolveRoutes: suspend (Map<String, dev.triplet.app.core.AppRoute>) -> EffectiveRoutes,
    private val hasVpnPermission: () -> Boolean,
    private val isEngineReady: () -> Boolean,
    private val readSubscriptionNode: () -> String?,
    private val probeVpn: () -> Boolean,
    private val probeDpi: () -> Boolean,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiagnosticsUiState(vpnState = vpnState.value))
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()
    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null

    init {
        refresh(runProbes = false)
        viewModelScope.launch {
            vpnState.collect {
                refresh(runProbes = false)
            }
        }
    }

    fun refresh(runProbes: Boolean = true) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            refreshMutex.withLock {
                if (runProbes) {
                    _uiState.value = _uiState.value.copy(
                        checking = true,
                        vpnProbe = DiagnosticProbeState.RUNNING,
                        dpiProbe = DiagnosticProbeState.RUNNING,
                        vpnLatencyMs = null,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(loading = true)
                }

                try {
                    val settings = loadSettings()
                    val effectiveRoutes = withContext(Dispatchers.IO) {
                        resolveRoutes(settings.routes)
                    }
                    val currentVpnState = vpnState.value
                    val active = currentVpnState == VpnState.Active
                    val engineReady = active && runCatching(isEngineReady).getOrDefault(false)
                    val vpnRouteCount = effectiveRoutes.vpnPackages.size
                    val dpiRouteCount = effectiveRoutes.dpiPackages.size
                    val activeKey = settings.vlessKeys.active
                    val serverLabel = when (settings.activeVpn) {
                        VpnProfileKind.VLESS -> {
                            val parsed = activeKey?.uri?.let(VlessKeyParser::parse) as? ParseResult.Ok
                            parsed?.profile?.server
                        }
                        VpnProfileKind.SUBSCRIPTION -> if (active) {
                            withContext(Dispatchers.IO) {
                                runCatching(readSubscriptionNode).getOrNull()
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                            }
                        } else {
                            activeKey?.selectedNode
                        }
                        VpnProfileKind.WARP -> null
                    }
                    val profileName = when (settings.activeVpn) {
                        VpnProfileKind.VLESS, VpnProfileKind.SUBSCRIPTION -> activeKey?.name
                        VpnProfileKind.WARP -> settings.warpProfile?.name
                    }
                    val endpointCount = when (settings.activeVpn) {
                        VpnProfileKind.WARP -> settings.warpProfile?.proxies?.size ?: 0
                        else -> 0
                    }
                    val multiHopEntry = settings.multiHopEntry
                    val multiHopEnabled = multiHopEntry != null
                    val multiHopValid = runCatching {
                        resolveMultiHopEntry(settings)
                        true
                    }.getOrDefault(false)
                    val multiHopEntryLabel = when (multiHopEntry) {
                        is MultiHopEntryRef.Vless -> settings.vlessKeys.items
                            .firstOrNull { it.id == multiHopEntry.keyId }
                            ?.name
                            ?.takeIf { it.isNotBlank() }
                            ?: "VLESS"
                        MultiHopEntryRef.Warp -> "WARP"
                        MultiHopEntryRef.Invalid, null -> null
                    }
                    val dnsId = settings.dnsId.ifBlank { "google" }
                    val dnsValid = DnsOptions.isSelectionValid(dnsId, settings.dnsCustom)

                    var vpnProbeState = if (vpnRouteCount == 0 || !active || !engineReady) {
                        DiagnosticProbeState.NOT_APPLICABLE
                    } else {
                        DiagnosticProbeState.NOT_RUN
                    }
                    var dpiProbeState = if (dpiRouteCount == 0 || !active || !engineReady) {
                        DiagnosticProbeState.NOT_APPLICABLE
                    } else {
                        DiagnosticProbeState.NOT_RUN
                    }
                    var vpnLatencyMs: Long? = null

                    if (runProbes && active && engineReady) {
                        if (vpnRouteCount > 0) {
                            val (healthy, latencyMs) = withContext(Dispatchers.IO) {
                                val startedAt = System.nanoTime()
                                val ok = runCatching(probeVpn).getOrDefault(false)
                                val elapsedMs = ((System.nanoTime() - startedAt) / 1_000_000L)
                                    .coerceAtLeast(0L)
                                ok to elapsedMs
                            }
                            vpnProbeState = if (healthy) {
                                vpnLatencyMs = latencyMs
                                DiagnosticProbeState.PASS
                            } else {
                                DiagnosticProbeState.FAIL
                            }
                        }
                        if (dpiRouteCount > 0) {
                            dpiProbeState = withContext(Dispatchers.IO) {
                                if (runCatching(probeDpi).getOrDefault(false)) {
                                    DiagnosticProbeState.PASS
                                } else {
                                    DiagnosticProbeState.FAIL
                                }
                            }
                        }
                    }

                    _uiState.value = DiagnosticsUiState(
                        loading = false,
                        checking = false,
                        vpnPermissionGranted = runCatching(hasVpnPermission).getOrDefault(false),
                        vpnState = currentVpnState,
                        engineReady = engineReady,
                        vpnProbe = vpnProbeState,
                        dpiProbe = dpiProbeState,
                        vpnLatencyMs = vpnLatencyMs,
                        profileKind = settings.activeVpn,
                        profileName = profileName,
                        serverLabel = serverLabel,
                        endpointCount = endpointCount,
                        multiHopEnabled = multiHopEnabled,
                        multiHopEntryLabel = multiHopEntryLabel,
                        multiHopValid = multiHopValid,
                        dnsId = dnsId,
                        dnsValid = dnsValid,
                        vpnRouteCount = vpnRouteCount,
                        dpiRouteCount = dpiRouteCount,
                        lastError = (currentVpnState as? VpnState.Failed)?.reason?.let(::redactDiagnosticText),
                        checkedAt = if (runProbes) System.currentTimeMillis() else _uiState.value.checkedAt,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        checking = false,
                        vpnProbe = DiagnosticProbeState.FAIL,
                        dpiProbe = DiagnosticProbeState.FAIL,
                        vpnLatencyMs = null,
                        lastError = redactDiagnosticText(e.message ?: e.javaClass.simpleName),
                        checkedAt = if (runProbes) System.currentTimeMillis() else _uiState.value.checkedAt,
                    )
                }
            }
        }
    }

    fun report(): String = buildDiagnosticsReport(_uiState.value)

    companion object {
        fun factory(
            store: RoutesStore,
            resolveRoutes: suspend (Map<String, dev.triplet.app.core.AppRoute>) -> EffectiveRoutes,
            hasVpnPermission: () -> Boolean,
            isEngineReady: () -> Boolean,
            readSubscriptionNode: () -> String?,
            probeVpn: () -> Boolean,
            probeDpi: () -> Boolean,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(DiagnosticsViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return DiagnosticsViewModel(
                    loadSettings = store::snapshot,
                    vpnState = VpnController.state,
                    resolveRoutes = resolveRoutes,
                    hasVpnPermission = hasVpnPermission,
                    isEngineReady = isEngineReady,
                    readSubscriptionNode = readSubscriptionNode,
                    probeVpn = probeVpn,
                    probeDpi = probeDpi,
                ) as T
            }
        }
    }
}

private val diagnosticUrl = Regex("(?i)https?://[^\\s]+")
private val diagnosticUuid = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b")
private val diagnosticSecretAssignment = Regex("(?i)(token|password|passwd|authorization|uuid|private[-_ ]?key|public[-_ ]?key)\\s*[:=]\\s*[^\\s,;]+")
private val diagnosticLongToken = Regex("\\b[A-Za-z0-9_-]{32,}\\b")

internal fun redactDiagnosticText(value: String): String =
    value
        .replace(diagnosticUrl, "[redacted-url]")
        .replace(diagnosticSecretAssignment) { "${it.groupValues[1]}=[redacted]" }
        .replace(diagnosticUuid, "[redacted-uuid]")
        .replace(diagnosticLongToken, "[redacted]")
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .trim()
        .take(240)

private fun vpnStateCode(state: VpnState): String = when (state) {
    VpnState.Idle -> "IDLE"
    VpnState.Starting -> "STARTING"
    VpnState.Active -> "ACTIVE"
    is VpnState.Failed -> "FAILED"
}

internal fun buildDiagnosticsReport(state: DiagnosticsUiState): String = buildString {
    appendLine("Detour diagnostics")
    appendLine("vpn_permission=${state.vpnPermissionGranted}")
    appendLine("vpn_state=${vpnStateCode(state.vpnState)}")
    appendLine("engine_ready=${state.engineReady}")
    appendLine("vpn_probe=${state.vpnProbe.name}")
    appendLine("vpn_latency_ms=${state.vpnLatencyMs ?: "none"}")
    appendLine("dpi_probe=${state.dpiProbe.name}")
    appendLine("profile_kind=${state.profileKind.name}")
    appendLine("profile_name=${state.profileName?.let(::redactDiagnosticText) ?: "none"}")
    appendLine("server=${state.serverLabel?.let(::redactDiagnosticText) ?: "none"}")
    appendLine("endpoint_count=${state.endpointCount}")
    appendLine("multi_hop_enabled=${state.multiHopEnabled}")
    appendLine("multi_hop_valid=${state.multiHopValid}")
    appendLine("multi_hop_entry=${state.multiHopEntryLabel?.let(::redactDiagnosticText) ?: "none"}")
    appendLine("dns=${state.dnsId}")
    appendLine("dns_valid=${state.dnsValid}")
    appendLine("vpn_routes=${state.vpnRouteCount}")
    appendLine("dpi_routes=${state.dpiRouteCount}")
    appendLine("last_error=${state.lastError?.let(::redactDiagnosticText) ?: "none"}")
    append("checked_at=${state.checkedAt ?: 0L}")
}
