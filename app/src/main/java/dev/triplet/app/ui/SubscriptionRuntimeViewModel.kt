package dev.triplet.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.SubscriptionProviderState
import dev.triplet.app.core.SubscriptionProviderStateParser
import dev.triplet.engine.engine.Engine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class SubscriptionRuntimeStatus { IDLE, LOADING, REFRESHING, ERROR }
enum class SubscriptionCatalogStatus { IDLE, LOADING, READY, ERROR }
enum class SubscriptionSelectionStatus { IDLE, SAVING, ERROR }

data class SubscriptionCatalogNode(
    val name: String,
    val type: String,
)

data class SubscriptionMetadata(
    val title: String? = null,
    val updateIntervalHours: Int? = null,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val totalBytes: Long? = null,
    val expireAtUnix: Long? = null,
    val supportUrl: String? = null,
    val profileWebPageUrl: String? = null,
    val announcement: String? = null,
) {
    val usedBytes: Long get() = (uploadBytes + downloadBytes).coerceAtLeast(0)
    val remainingBytes: Long? get() = totalBytes?.let { (it - usedBytes).coerceAtLeast(0) }

    val isEmpty: Boolean get() =
        title == null && updateIntervalHours == null && totalBytes == null && expireAtUnix == null &&
            supportUrl == null && profileWebPageUrl == null && announcement == null && usedBytes == 0L
}

data class SubscriptionLatencyError(
    val errorClass: String,
    val errorText: String,
)

data class SubscriptionLatencyResult(
    val testedNames: Set<String> = emptySet(),
    val delayByName: Map<String, Int> = emptyMap(),
    val errorByName: Map<String, SubscriptionLatencyError> = emptyMap(),
)

data class SubscriptionRuntimeUiState(
    val provider: SubscriptionProviderState = SubscriptionProviderState.Unavailable,
    val catalog: List<SubscriptionCatalogNode> = emptyList(),
    val metadata: SubscriptionMetadata? = null,
    val status: SubscriptionRuntimeStatus = SubscriptionRuntimeStatus.IDLE,
    val catalogStatus: SubscriptionCatalogStatus = SubscriptionCatalogStatus.IDLE,
    val selectedNode: String? = null,
    val selectionStatus: SubscriptionSelectionStatus = SubscriptionSelectionStatus.IDLE,
    val latencyTesting: Boolean = false,
    val latencyTestedNames: Set<String> = emptySet(),
    val latencyByName: Map<String, Int> = emptyMap(),
    val latencyErrorByName: Map<String, SubscriptionLatencyError> = emptyMap(),
)

private fun safeLatencyDiagnostic(value: String, maxChars: Int): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank() || trimmed.length > maxChars) return null
    if (trimmed.any { it.code < 0x20 || it.code == 0x7f }) return null
    return trimmed
}

internal fun retainedSubscriptionSelection(selectedNode: String?, availableNames: Set<String>): String? =
    selectedNode?.takeIf { it in availableNames }

internal fun parseSubscriptionMetadata(raw: String): SubscriptionMetadata? {
    if (raw.isBlank() || raw.length > MAX_METADATA_JSON_CHARS) return null
    return runCatching {
        val json = JSONObject(raw)
        fun text(name: String, maxChars: Int): String? =
            safeLatencyDiagnostic(json.optString(name), maxChars)
        fun positiveLong(name: String): Long? =
            if (!json.has(name)) null else json.optLong(name, 0L).takeIf { it > 0L }

        val metadata = SubscriptionMetadata(
            title = text("title", 256),
            updateIntervalHours = json.optInt("updateIntervalHours", 0).takeIf { it in 1..24 * 365 },
            uploadBytes = json.optLong("uploadBytes", 0L).coerceAtLeast(0L),
            downloadBytes = json.optLong("downloadBytes", 0L).coerceAtLeast(0L),
            totalBytes = positiveLong("totalBytes"),
            expireAtUnix = positiveLong("expireAtUnix"),
            supportUrl = text("supportUrl", 2048),
            profileWebPageUrl = text("profileWebPageUrl", 2048),
            announcement = text("announcement", 2048),
        )
        metadata.takeUnless { it.isEmpty }
    }.getOrNull()
}

internal fun parseSubscriptionLatencyResult(raw: String): SubscriptionLatencyResult {
    if (raw.isBlank() || raw.length > 512 * 1024) return SubscriptionLatencyResult()
    return runCatching {
        val nodes = JSONObject(raw).optJSONArray("nodes") ?: return@runCatching SubscriptionLatencyResult()
        val tested = LinkedHashSet<String>()
        val delays = LinkedHashMap<String, Int>()
        val errors = LinkedHashMap<String, SubscriptionLatencyError>()
        for (index in 0 until minOf(nodes.length(), 256)) {
            val node = nodes.optJSONObject(index) ?: continue
            val name = node.optString("name").trim()
            if (
                name.isBlank() || name.length > 256 ||
                name.any { it.code < 0x20 || it.code == 0x7f }
            ) continue
            tested += name
            val delay = node.optInt("delayMs", 0)
            if (delay in 1..60_000) {
                delays[name] = delay
                continue
            }

            val errorClass = safeLatencyDiagnostic(node.optString("errorClass"), 64)
            val errorText = safeLatencyDiagnostic(node.optString("errorText"), 800)
            if (errorClass != null && errorText != null) {
                errors[name] = SubscriptionLatencyError(errorClass, errorText)
            }
        }
        SubscriptionLatencyResult(
            testedNames = tested,
            delayByName = delays,
            errorByName = errors,
        )
    }.getOrDefault(SubscriptionLatencyResult())
}

class SubscriptionRuntimeViewModel : ViewModel() {
    private val operationMutex = Mutex()
    private val _uiState = MutableStateFlow(SubscriptionRuntimeUiState())
    val uiState: StateFlow<SubscriptionRuntimeUiState> = _uiState.asStateFlow()

    private var boundUrl: String? = null
    private var boundCacheDir: String = ""
    private var catalogJob: Job? = null
    private var metadataJob: Job? = null
    private var runtimeJob: Job? = null

    fun bind(
        subscriptionUrl: String,
        connected: Boolean,
        cacheDir: String,
        persistedSelectedNode: String? = null,
    ) {
        val previousUrl = boundUrl
        val previousSelected = _uiState.value.selectedNode
        val changed = boundUrl != subscriptionUrl || boundCacheDir != cacheDir
        boundUrl = subscriptionUrl
        boundCacheDir = cacheDir
        val durableSelected = persistedSelectedNode
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 256 }

        if (changed) {
            runtimeJob?.cancel()
            metadataJob?.cancel()
            _uiState.value = SubscriptionRuntimeUiState(
                selectedNode = durableSelected ?: previousSelected.takeIf { previousUrl == subscriptionUrl },
            )
            loadCatalog(subscriptionUrl)
            loadMetadata(subscriptionUrl)
        } else if (_uiState.value.catalogStatus == SubscriptionCatalogStatus.IDLE) {
            loadCatalog(subscriptionUrl)
            loadMetadata(subscriptionUrl)
        } else if (durableSelected != null && _uiState.value.selectedNode.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(selectedNode = durableSelected)
        }

        if (connected) {
            loadRuntimeWithPolling()
        } else {
            runtimeJob?.cancel()
            _uiState.value = _uiState.value.copy(
                provider = SubscriptionProviderState.Unavailable,
                status = SubscriptionRuntimeStatus.IDLE,
            )
        }
    }

    fun refresh(connected: Boolean) {
        val url = boundUrl ?: return
        _uiState.value = _uiState.value.copy(
            latencyTesting = false,
            latencyTestedNames = emptySet(),
            latencyByName = emptyMap(),
            latencyErrorByName = emptyMap(),
        )
        loadCatalog(url, force = true)
        loadMetadata(url, force = true)
        if (connected) {
            runtimeJob?.cancel()
            runtimeJob = viewModelScope.launch {
                operationMutex.withLock {
                    try {
                        _uiState.value = _uiState.value.copy(status = SubscriptionRuntimeStatus.REFRESHING)
                        withContext(Dispatchers.IO) {
                            val path = Engine.prepareSubscriptionProvider(url, boundCacheDir)
                            check(path.isNotBlank()) { "subscription could not be prepared" }
                            Engine.refreshSubscriptionProvider()
                        }
                        val provider = pollProvider()
                        val selected = withContext(Dispatchers.IO) {
                            Engine.subscriptionSelectedNode(boundCacheDir)
                        }.takeIf { it.isNotBlank() }
                        _uiState.value = _uiState.value.copy(
                            provider = provider,
                            selectedNode = selected ?: _uiState.value.selectedNode,
                            status = SubscriptionRuntimeStatus.IDLE,
                        )
                        selected?.let { logSelectedNodeDiagnostics(boundCacheDir, it) }
                    } catch (cancelled: CancellationException) {
                        _uiState.value = _uiState.value.copy(status = SubscriptionRuntimeStatus.IDLE)
                        throw cancelled
                    } catch (_: Exception) {
                        _uiState.value = _uiState.value.copy(status = SubscriptionRuntimeStatus.ERROR)
                    }
                }
            }
        }
    }

    fun testLatency() {
        val url = boundUrl ?: return
        if (_uiState.value.latencyTesting || _uiState.value.catalog.isEmpty()) return
        viewModelScope.launch {
            operationMutex.withLock {
                try {
                    _uiState.value = _uiState.value.copy(
                        latencyTesting = true,
                        latencyTestedNames = emptySet(),
                        latencyByName = emptyMap(),
                        latencyErrorByName = emptyMap(),
                    )
                    val result = withContext(Dispatchers.IO) {
                        parseSubscriptionLatencyResult(Engine.testSubscriptionCatalogLatencyDetached(url))
                    }
                    _uiState.value = _uiState.value.copy(
                        latencyTesting = false,
                        latencyTestedNames = result.testedNames,
                        latencyByName = result.delayByName,
                        latencyErrorByName = result.errorByName,
                    )
                } catch (cancelled: CancellationException) {
                    _uiState.value = _uiState.value.copy(latencyTesting = false)
                    throw cancelled
                } catch (_: Exception) {
                    _uiState.value = _uiState.value.copy(latencyTesting = false)
                }
            }
        }
    }

    fun selectNode(
        name: String,
        onSelected: suspend (String) -> Unit = {},
    ) {
        if (name.isBlank() || _uiState.value.selectionStatus == SubscriptionSelectionStatus.SAVING) return
        val previous = _uiState.value.selectedNode
        _uiState.value = _uiState.value.copy(
            selectedNode = name,
            selectionStatus = SubscriptionSelectionStatus.SAVING,
        )
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Engine.selectSubscriptionNode(name, boundCacheDir)
                }
                val selected = withContext(Dispatchers.IO) {
                    Engine.subscriptionSelectedNode(boundCacheDir)
                }.takeIf { it.isNotBlank() } ?: name
                onSelected(selected)
                _uiState.value = _uiState.value.copy(
                    selectedNode = selected,
                    selectionStatus = SubscriptionSelectionStatus.IDLE,
                )
                logSelectedNodeDiagnostics(boundCacheDir, selected)
            } catch (cancelled: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    selectedNode = previous,
                    selectionStatus = SubscriptionSelectionStatus.IDLE,
                )
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    selectedNode = previous,
                    selectionStatus = SubscriptionSelectionStatus.ERROR,
                )
            }
        }
    }

    private fun loadCatalog(url: String, force: Boolean = false) {
        if (!force && boundUrl == url && _uiState.value.catalogStatus == SubscriptionCatalogStatus.LOADING) return
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(catalogStatus = SubscriptionCatalogStatus.LOADING)
                val nodes = withContext(Dispatchers.IO) {
                    parseCatalog(Engine.fetchPreparedSubscriptionCatalog(url))
                }
                val names = nodes.mapTo(HashSet()) { it.name }
                _uiState.value = _uiState.value.copy(
                    catalog = nodes,
                    catalogStatus = if (nodes.isEmpty()) {
                        SubscriptionCatalogStatus.ERROR
                    } else {
                        SubscriptionCatalogStatus.READY
                    },
                    selectedNode = retainedSubscriptionSelection(_uiState.value.selectedNode, names),
                    latencyTestedNames = _uiState.value.latencyTestedNames intersect names,
                    latencyByName = _uiState.value.latencyByName.filterKeys { it in names },
                    latencyErrorByName = _uiState.value.latencyErrorByName.filterKeys { it in names },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(catalogStatus = SubscriptionCatalogStatus.ERROR)
            }
        }
    }

    private fun loadMetadata(url: String, force: Boolean = false) {
        if (!force && metadataJob?.isActive == true) return
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            try {
                val metadata = withContext(Dispatchers.IO) {
                    parseSubscriptionMetadata(Engine.fetchSubscriptionMetadata(url))
                }
                if (boundUrl == url) {
                    _uiState.value = _uiState.value.copy(metadata = metadata)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (boundUrl == url) {
                    _uiState.value = _uiState.value.copy(metadata = null)
                }
            }
        }
    }

    private fun loadRuntimeWithPolling() {
        if (runtimeJob?.isActive == true && _uiState.value.provider.available) return
        runtimeJob?.cancel()
        runtimeJob = viewModelScope.launch {
            operationMutex.withLock {
                try {
                    _uiState.value = _uiState.value.copy(status = SubscriptionRuntimeStatus.LOADING)
                    val provider = pollProvider()
                    val selected = withContext(Dispatchers.IO) {
                        Engine.subscriptionSelectedNode(boundCacheDir)
                    }.takeIf { it.isNotBlank() }
                    _uiState.value = _uiState.value.copy(
                        provider = provider,
                        selectedNode = selected ?: _uiState.value.selectedNode,
                        status = if (provider.available) {
                            SubscriptionRuntimeStatus.IDLE
                        } else {
                            SubscriptionRuntimeStatus.ERROR
                        },
                    )
                    val node = selected ?: _uiState.value.selectedNode
                    if (node != null) {
                        logSelectedNodeDiagnostics(boundCacheDir, node)
                    }
                } catch (cancelled: CancellationException) {
                    _uiState.value = _uiState.value.copy(status = SubscriptionRuntimeStatus.IDLE)
                    throw cancelled
                } catch (_: Exception) {
                    _uiState.value = _uiState.value.copy(status = SubscriptionRuntimeStatus.ERROR)
                }
            }
        }
    }

    private suspend fun logSelectedNodeDiagnostics(homeDir: String, nodeName: String) {
        val diagnostics = withContext(Dispatchers.IO) {
            Engine.subscriptionNodeDiagnostics(homeDir, nodeName)
        }
        if (diagnostics.isNotBlank()) {
            Log.i(PROXY_CONFIG_LOG_TAG, "[DETOUR_PROXY_CONFIG] $diagnostics")
        }
    }

    private suspend fun pollProvider(): SubscriptionProviderState {
        var last = SubscriptionProviderState.Unavailable
        repeat(PROVIDER_POLL_ATTEMPTS) { attempt ->
            last = withContext(Dispatchers.IO) {
                SubscriptionProviderStateParser.parse(Engine.subscriptionProviderState())
            }
            if (last.available && last.nodes.isNotEmpty()) return last
            if (attempt < PROVIDER_POLL_ATTEMPTS - 1) delay(PROVIDER_POLL_DELAY_MS)
        }
        return last
    }

    private fun parseCatalog(raw: String): List<SubscriptionCatalogNode> {
        if (raw.isBlank() || raw.length > MAX_CATALOG_JSON_CHARS) return emptyList()
        return runCatching {
            val nodes = JSONObject(raw).optJSONArray("nodes") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until minOf(nodes.length(), MAX_CATALOG_NODES)) {
                    val node = nodes.optJSONObject(index) ?: continue
                    val name = safeLabel(node.optString("name"), 256) ?: continue
                    val type = safeLabel(node.optString("type"), 64) ?: continue
                    if (!type.equals("vless", ignoreCase = true)) continue
                    add(SubscriptionCatalogNode(name, type))
                }
            }.distinctBy { it.name }
        }.getOrDefault(emptyList())
    }

    private fun safeLabel(value: String, maxChars: Int): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.length > maxChars) return null
        if (trimmed.any { it.code < 0x20 || it.code == 0x7f }) return null
        return trimmed
    }

    private companion object {
        const val PROVIDER_POLL_ATTEMPTS = 24
        const val PROVIDER_POLL_DELAY_MS = 350L
        const val MAX_CATALOG_NODES = 256
        const val MAX_CATALOG_JSON_CHARS = 512 * 1024
        const val MAX_METADATA_JSON_CHARS = 32 * 1024
        const val PROXY_CONFIG_LOG_TAG = "DetourProxyConfig"
    }
}
