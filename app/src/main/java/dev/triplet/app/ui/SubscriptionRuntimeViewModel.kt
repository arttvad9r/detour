package dev.triplet.app.ui

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

data class SubscriptionRuntimeUiState(
    val provider: SubscriptionProviderState = SubscriptionProviderState.Unavailable,
    val catalog: List<SubscriptionCatalogNode> = emptyList(),
    val status: SubscriptionRuntimeStatus = SubscriptionRuntimeStatus.IDLE,
    val catalogStatus: SubscriptionCatalogStatus = SubscriptionCatalogStatus.IDLE,
    val selectedNode: String? = null,
    val selectionStatus: SubscriptionSelectionStatus = SubscriptionSelectionStatus.IDLE,
)

class SubscriptionRuntimeViewModel : ViewModel() {
    private val operationMutex = Mutex()
    private val _uiState = MutableStateFlow(SubscriptionRuntimeUiState())
    val uiState: StateFlow<SubscriptionRuntimeUiState> = _uiState.asStateFlow()

    private var boundUrl: String? = null
    private var boundCacheDir: String = ""
    private var catalogJob: Job? = null
    private var runtimeJob: Job? = null

    fun bind(subscriptionUrl: String, connected: Boolean, cacheDir: String) {
        val previousUrl = boundUrl
        val previousSelected = _uiState.value.selectedNode
        val changed = boundUrl != subscriptionUrl || boundCacheDir != cacheDir
        boundUrl = subscriptionUrl
        boundCacheDir = cacheDir

        if (changed) {
            runtimeJob?.cancel()
            val cachedSelected = runCatching { Engine.subscriptionSelectedNode(cacheDir) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            _uiState.value = SubscriptionRuntimeUiState(
                selectedNode = cachedSelected ?: previousSelected.takeIf { previousUrl == subscriptionUrl },
            )
            loadCatalog(subscriptionUrl)
        } else if (_uiState.value.catalogStatus == SubscriptionCatalogStatus.IDLE) {
            loadCatalog(subscriptionUrl)
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
        loadCatalog(url, force = true)
        if (connected) {
            runtimeJob?.cancel()
            runtimeJob = viewModelScope.launch {
                operationMutex.withLock {
                    try {
                        _uiState.value = _uiState.value.copy(status = SubscriptionRuntimeStatus.REFRESHING)
                        withContext(Dispatchers.IO) { Engine.refreshSubscriptionProvider() }
                        val provider = pollProvider()
                        val selected = withContext(Dispatchers.IO) {
                            Engine.subscriptionSelectedNode(boundCacheDir)
                        }.takeIf { it.isNotBlank() }
                        _uiState.value = _uiState.value.copy(
                            provider = provider,
                            selectedNode = selected ?: _uiState.value.selectedNode,
                            status = SubscriptionRuntimeStatus.IDLE,
                        )
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

    fun selectNode(name: String) {
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
                _uiState.value = _uiState.value.copy(
                    selectedNode = selected,
                    selectionStatus = SubscriptionSelectionStatus.IDLE,
                )
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

    fun clearSelectionError() {
        if (_uiState.value.selectionStatus == SubscriptionSelectionStatus.ERROR) {
            _uiState.value = _uiState.value.copy(selectionStatus = SubscriptionSelectionStatus.IDLE)
        }
    }

    private fun loadCatalog(url: String, force: Boolean = false) {
        if (!force && boundUrl == url && _uiState.value.catalogStatus == SubscriptionCatalogStatus.LOADING) return
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(catalogStatus = SubscriptionCatalogStatus.LOADING)
                val nodes = withContext(Dispatchers.IO) {
                    parseCatalog(Engine.fetchSubscriptionCatalog(url))
                }
                _uiState.value = _uiState.value.copy(
                    catalog = nodes,
                    catalogStatus = if (nodes.isEmpty()) {
                        SubscriptionCatalogStatus.ERROR
                    } else {
                        SubscriptionCatalogStatus.READY
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(catalogStatus = SubscriptionCatalogStatus.ERROR)
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
                } catch (cancelled: CancellationException) {
                    _uiState.value = _uiState.value.copy(status = SubscriptionRuntimeStatus.IDLE)
                    throw cancelled
                } catch (_: Exception) {
                    _uiState.value = _uiState.value.copy(status = SubscriptionRuntimeStatus.ERROR)
                }
            }
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
                    val type = safeLabel(node.optString("type"), 64) ?: "unknown"
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
    }
}
