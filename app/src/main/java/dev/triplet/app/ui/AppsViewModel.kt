package dev.triplet.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.triplet.app.core.AppRoute
import dev.triplet.app.data.AppInfo
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

private fun routeFor(settings: TriSettings?, packageName: String): AppRoute =
    settings?.routes?.get(packageName) ?: AppRoute.DIRECT

private fun applyRouteOverrides(
    persisted: Map<String, AppRoute>,
    overrides: Map<String, AppRoute>,
): Map<String, AppRoute> = persisted.toMutableMap().apply {
    overrides.forEach { (packageName, route) ->
        if (route == AppRoute.DIRECT) remove(packageName) else put(packageName, route)
    }
}

internal fun appsUiState(
    settings: TriSettings?,
    inventory: AppsInventoryState,
    query: String,
    showSystemOverride: Boolean? = null,
    routeOverrides: Map<String, AppRoute> = emptyMap(),
): AppsUiState = AppsUiState(
    loadedApps = inventory.apps,
    routes = applyRouteOverrides(settings?.routes.orEmpty(), routeOverrides),
    showSystemApps = showSystemOverride ?: (settings?.showSystemApps ?: false),
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
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val inventory = MutableStateFlow(AppsInventoryState(initialApps))
    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val showSystemOverride = MutableStateFlow<Boolean?>(null)
    private val routeOverrides = MutableStateFlow<Map<String, AppRoute>>(emptyMap())
    private val showSystemWriteMutex = Mutex()
    private val routeWriteMutex = Mutex()
    private var refreshJob: Job? = null

    val uiState: StateFlow<AppsUiState> = combine(
        settings,
        inventory,
        query,
        showSystemOverride,
        routeOverrides,
        ::appsUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = appsUiState(settings.value, inventory.value, query.value),
    )

    fun setQuery(value: String) {
        savedStateHandle[KEY_QUERY] = value
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
        val currentIntent = showSystemOverride.value ?: (settings.value?.showSystemApps ?: false)
        if (currentIntent == value) return
        showSystemOverride.value = value

        viewModelScope.launch {
            showSystemWriteMutex.withLock {
                val desired = showSystemOverride.value ?: return@withLock
                try {
                    if (settings.value?.showSystemApps != desired) {
                        setShowSystemApps(desired)
                    }
                    if (settings.value?.showSystemApps != desired) {
                        settings.first { it?.showSystemApps == desired }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (showSystemOverride.value == desired) showSystemOverride.value = null
                    return@withLock
                }

                if (showSystemOverride.value == desired) showSystemOverride.value = null
            }
        }
    }

    fun setAppRoute(packageName: String, route: AppRoute) {
        val currentIntent = routeOverrides.value[packageName] ?: routeFor(settings.value, packageName)
        if (currentIntent == route) return
        routeOverrides.value = routeOverrides.value + (packageName to route)

        viewModelScope.launch {
            routeWriteMutex.withLock {
                val desired = routeOverrides.value[packageName] ?: return@withLock
                try {
                    if (routeFor(settings.value, packageName) != desired) {
                        setRoute(packageName, desired)
                        if (routeFor(settings.value, packageName) != desired) {
                            settings.first { routeFor(it, packageName) == desired }
                        }
                        restartTunnel()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (routeOverrides.value[packageName] == desired) {
                        routeOverrides.value = routeOverrides.value - packageName
                    }
                    return@withLock
                }

                if (routeOverrides.value[packageName] == desired) {
                    routeOverrides.value = routeOverrides.value - packageName
                }
            }
        }
    }

    companion object {
        private const val KEY_QUERY = "apps_query"

        fun factory(
            store: RoutesStore,
            initialApps: List<AppInfo>?,
            loadApps: suspend () -> List<AppInfo>,
            restartTunnel: () -> Unit,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppsViewModel(
                    settings = store.settings,
                    initialApps = initialApps,
                    loadApps = loadApps,
                    setShowSystemApps = store::setShowSystemApps,
                    setRoute = store::setRoute,
                    restartTunnel = restartTunnel,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
