package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.AppRoute
import dev.triplet.app.data.AppInfo
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppsInventoryStatus { LOADING, READY, ERROR }

internal data class AppsInventoryState(
    val apps: List<AppInfo>? = null,
    val status: AppsInventoryStatus = if (apps == null) AppsInventoryStatus.LOADING else AppsInventoryStatus.READY,
)

data class AppsUiState(
    val loadedApps: List<AppInfo>? = null,
    val routes: Map<String, AppRoute> = emptyMap(),
    val showSystemApps: Boolean = false,
    val query: String = "",
    val inventoryStatus: AppsInventoryStatus = if (loadedApps == null) {
        AppsInventoryStatus.LOADING
    } else {
        AppsInventoryStatus.READY
    },
)

internal fun appsUiState(
    settings: TriSettings?,
    inventory: AppsInventoryState,
    query: String,
): AppsUiState = AppsUiState(
    loadedApps = inventory.apps,
    routes = settings?.routes.orEmpty(),
    showSystemApps = settings?.showSystemApps ?: false,
    query = query,
    inventoryStatus = inventory.status,
)

internal fun appsInventoryRefreshing(previous: AppsInventoryState): AppsInventoryState = previous.copy(
    status = if (previous.apps == null) AppsInventoryStatus.LOADING else AppsInventoryStatus.READY,
)

internal fun appsInventoryLoaded(apps: List<AppInfo>): AppsInventoryState =
    AppsInventoryState(apps, AppsInventoryStatus.READY)

internal fun appsInventoryFailed(previous: AppsInventoryState): AppsInventoryState =
    previous.copy(status = AppsInventoryStatus.ERROR)

class AppsViewModel(
    private val settings: StateFlow<TriSettings?>,
    initialApps: List<AppInfo>?,
    private val loadApps: suspend () -> List<AppInfo>,
    private val setShowSystemApps: suspend (Boolean) -> Unit,
    private val setRoute: suspend (String, AppRoute) -> Unit,
    private val restartTunnel: () -> Unit,
) : ViewModel() {
    private val inventory = MutableStateFlow(AppsInventoryState(initialApps))
    private val query = MutableStateFlow("")
    private var refreshJob: Job? = null

    val uiState: StateFlow<AppsUiState> = combine(
        settings,
        inventory,
        query,
        ::appsUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = appsUiState(settings.value, inventory.value, ""),
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun refreshInventory() {
        if (refreshJob?.isActive == true) return
        val previous = inventory.value
        inventory.value = appsInventoryRefreshing(previous)
        refreshJob = viewModelScope.launch {
            inventory.value = runCatching { loadApps() }
                .fold(
                    onSuccess = ::appsInventoryLoaded,
                    onFailure = { appsInventoryFailed(previous) },
                )
        }
    }

    fun setShowSystem(value: Boolean) {
        if (settings.value?.showSystemApps == value) return
        viewModelScope.launch { setShowSystemApps(value) }
    }

    fun setAppRoute(packageName: String, route: AppRoute) {
        val current = settings.value?.routes?.get(packageName) ?: AppRoute.DIRECT
        if (current == route) return
        viewModelScope.launch {
            setRoute(packageName, route)
            restartTunnel()
        }
    }

    companion object {
        fun factory(
            store: RoutesStore,
            initialApps: List<AppInfo>?,
            loadApps: suspend () -> List<AppInfo>,
            restartTunnel: () -> Unit,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(AppsViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return AppsViewModel(
                    settings = store.settings,
                    initialApps = initialApps,
                    loadApps = loadApps,
                    setShowSystemApps = store::setShowSystemApps,
                    setRoute = store::setRoute,
                    restartTunnel = restartTunnel,
                ) as T
            }
        }
    }
}
