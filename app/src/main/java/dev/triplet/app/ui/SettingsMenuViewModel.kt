package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.AppRoute
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import dev.triplet.app.vpn.EffectiveRoutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsMenuUiState(
    val routedCount: Int = 0,
    val hasVless: Boolean = false,
    val hasWarp: Boolean = false,
    val autoConnect: Boolean = false,
)

internal fun settingsMenuUiState(
    settings: TriSettings?,
    routedCount: Int,
): SettingsMenuUiState = SettingsMenuUiState(
    routedCount = routedCount,
    hasVless = settings?.vlessKeys?.items?.isNotEmpty() == true,
    hasWarp = settings?.warpProfile != null,
    autoConnect = settings?.autoConnect == true,
)

class SettingsMenuViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val resolveRoutes: suspend (Map<String, AppRoute>) -> EffectiveRoutes,
    private val persistAutoConnect: suspend (Boolean) -> Unit,
) : ViewModel() {
    private val routeRefresh = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val routedCount = combine(
        settings
            .map { it?.routes.orEmpty() }
            .distinctUntilChanged(),
        routeRefresh,
    ) { routes, _ -> routes }
        .mapLatest { routes ->
            if (routes.isEmpty()) 0 else resolveRoutes(routes).packages.size
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = settings.value?.routes?.size ?: 0,
        )

    val uiState: StateFlow<SettingsMenuUiState> = combine(
        settings,
        routedCount,
        ::settingsMenuUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = settingsMenuUiState(
            settings = settings.value,
            routedCount = settings.value?.routes?.size ?: 0,
        ),
    )

    fun refreshRoutes() {
        routeRefresh.value += 1
    }

    fun setAutoConnect(enabled: Boolean) {
        if (settings.value?.autoConnect == enabled) return
        viewModelScope.launch { persistAutoConnect(enabled) }
    }

    companion object {
        fun factory(
            store: RoutesStore,
            resolveRoutes: suspend (Map<String, AppRoute>) -> EffectiveRoutes,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(SettingsMenuViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return SettingsMenuViewModel(
                    settings = store.settings,
                    resolveRoutes = resolveRoutes,
                    persistAutoConnect = store::setAutoConnect,
                ) as T
            }
        }
    }
}
