package dev.detour.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.detour.app.core.AppRoute
import dev.detour.app.core.ParseResult
import dev.detour.app.core.VlessKeyParser
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.RoutesStore
import dev.detour.app.data.TriSettings
import dev.detour.app.vpn.EffectiveRoutes
import dev.detour.app.vpn.VpnController
import dev.detour.app.vpn.VpnState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SettingsMenuUiState(
    val routedCount: Int = 0,
    val hasVless: Boolean = false,
    val hasSubscription: Boolean = false,
    val hasWarp: Boolean = false,
    val autoConnect: Boolean = false,
    val vpnState: VpnState = VpnState.Idle,
    val sessionStartedAt: Long? = null,
    val protocol: HomeProtocol = HomeProtocol.NONE,
    val activeVpn: VpnProfileKind = VpnProfileKind.VLESS,
)

internal fun settingsMenuUiState(
    settings: TriSettings?,
    routedCount: Int,
    autoConnectOverride: Boolean? = null,
): SettingsMenuUiState {
    val keys = settings?.vlessKeys?.items.orEmpty()
    val subscriptionIds = keys.mapNotNull { key ->
        val parsed = VlessKeyParser.parse(key.uri) as? ParseResult.Ok
        key.id.takeIf { parsed?.profile?.isSubscription == true }
    }.toSet()
    return SettingsMenuUiState(
        routedCount = routedCount,
        hasVless = keys.any { it.id !in subscriptionIds },
        hasSubscription = subscriptionIds.isNotEmpty(),
        hasWarp = settings?.warpProfile != null,
        autoConnect = autoConnectOverride ?: (settings?.autoConnect == true),
        sessionStartedAt = settings?.sessionStartedAt,
        activeVpn = settings?.activeVpn ?: VpnProfileKind.VLESS,
    )
}

class SettingsMenuViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val vpnState: StateFlow<VpnState>,
    private val resolveRoutes: suspend (Map<String, AppRoute>) -> EffectiveRoutes,
    private val persistAutoConnect: suspend (Boolean) -> Unit,
) : ViewModel() {
    private val routeRefresh = MutableStateFlow(0L)
    private val autoConnectOverride = MutableStateFlow<Boolean?>(null)
    private val autoConnectWriteMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val effectiveRoutes = combine(
        settings
            .map { it?.routes.orEmpty() }
            .distinctUntilChanged(),
        routeRefresh,
    ) { routes, _ -> routes }
        .mapLatest { routes ->
            if (routes.isEmpty()) EffectiveRoutes(emptySet(), emptySet())
            else resolveRoutes(routes)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EffectiveRoutes(emptySet(), emptySet()),
        )

    val uiState: StateFlow<SettingsMenuUiState> = combine(
        settings,
        effectiveRoutes,
        autoConnectOverride,
        vpnState,
    ) { settings, effectiveRoutes, override, vpnState ->
        settingsMenuUiState(
            settings = settings,
            routedCount = effectiveRoutes.packages.size,
            autoConnectOverride = override,
        ).copy(
            vpnState = vpnState,
            protocol = homeProtocol(effectiveRoutes),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = settingsMenuUiState(
            settings = settings.value,
            routedCount = settings.value?.routes?.size ?: 0,
        ).copy(vpnState = vpnState.value),
    )

    fun refreshRoutes() {
        routeRefresh.value += 1
    }

    fun setAutoConnect(enabled: Boolean) {
        val currentIntent = autoConnectOverride.value ?: (settings.value?.autoConnect == true)
        if (currentIntent == enabled) return
        autoConnectOverride.value = enabled

        viewModelScope.launch {
            autoConnectWriteMutex.withLock {
                val desired = autoConnectOverride.value ?: return@withLock
                try {
                    if (settings.value?.autoConnect != desired) {
                        persistAutoConnect(desired)
                    }
                    if (settings.value?.autoConnect != desired) {
                        settings.first { it?.autoConnect == desired }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (autoConnectOverride.value == desired) autoConnectOverride.value = null
                    return@withLock
                }

                if (autoConnectOverride.value == desired) autoConnectOverride.value = null
            }
        }
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
                    vpnState = VpnController.state,
                    resolveRoutes = resolveRoutes,
                    persistAutoConnect = store::setAutoConnect,
                ) as T
            }
        }
    }
}
