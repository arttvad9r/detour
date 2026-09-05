package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import dev.triplet.app.vpn.EffectiveRoutes
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
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
    val autoConnectWifi: Boolean = false,
    val autoConnectCellular: Boolean = false,
    val vpnState: VpnState = VpnState.Idle,
    val sessionStartedAt: Long? = null,
    val protocol: HomeProtocol = HomeProtocol.NONE,
    val activeVpn: VpnProfileKind = VpnProfileKind.VLESS,
)

internal fun settingsMenuUiState(
    settings: TriSettings?,
    routedCount: Int,
    autoConnectOverride: Boolean? = null,
    autoConnectWifiOverride: Boolean? = null,
    autoConnectCellularOverride: Boolean? = null,
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
        autoConnectWifi = autoConnectWifiOverride ?: (settings?.autoConnectWifi == true),
        autoConnectCellular = autoConnectCellularOverride ?: (settings?.autoConnectCellular == true),
        sessionStartedAt = settings?.sessionStartedAt,
        activeVpn = settings?.activeVpn ?: VpnProfileKind.VLESS,
    )
}

class SettingsMenuViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val vpnState: StateFlow<VpnState>,
    private val resolveRoutes: suspend (Map<String, AppRoute>) -> EffectiveRoutes,
    private val persistAutoConnect: suspend (Boolean) -> Unit,
    private val persistAutoConnectWifi: suspend (Boolean) -> Unit,
    private val persistAutoConnectCellular: suspend (Boolean) -> Unit,
) : ViewModel() {
    private val routeRefresh = MutableStateFlow(0L)
    private val autoConnectOverride = MutableStateFlow<Boolean?>(null)
    private val autoConnectWifiOverride = MutableStateFlow<Boolean?>(null)
    private val autoConnectCellularOverride = MutableStateFlow<Boolean?>(null)
    private val autoConnectWriteMutex = Mutex()

    private val autoConnectOverrides = combine(
        autoConnectOverride,
        autoConnectWifiOverride,
        autoConnectCellularOverride,
    ) { launch, wifi, cellular -> Triple(launch, wifi, cellular) }

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
        autoConnectOverrides,
        vpnState,
    ) { settings, effectiveRoutes, overrides, vpnState ->
        settingsMenuUiState(
            settings = settings,
            routedCount = effectiveRoutes.packages.size,
            autoConnectOverride = overrides.first,
            autoConnectWifiOverride = overrides.second,
            autoConnectCellularOverride = overrides.third,
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
                        // Network triggers initially inherit the legacy launch flag.
                        // Persist their current effective values before changing that
                        // legacy source so toggling launch cannot silently change the
                        // Wi-Fi/mobile choices the user just saw in the UI.
                        settings.value?.let { current ->
                            persistAutoConnectWifi(current.autoConnectWifi)
                            persistAutoConnectCellular(current.autoConnectCellular)
                        }
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

    fun setAutoConnectWifi(enabled: Boolean) {
        val currentIntent = autoConnectWifiOverride.value ?: (settings.value?.autoConnectWifi == true)
        if (currentIntent == enabled) return
        autoConnectWifiOverride.value = enabled

        viewModelScope.launch {
            autoConnectWriteMutex.withLock {
                val desired = autoConnectWifiOverride.value ?: return@withLock
                try {
                    if (settings.value?.autoConnectWifi != desired) persistAutoConnectWifi(desired)
                    if (settings.value?.autoConnectWifi != desired) {
                        settings.first { it?.autoConnectWifi == desired }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (autoConnectWifiOverride.value == desired) autoConnectWifiOverride.value = null
                    return@withLock
                }

                if (autoConnectWifiOverride.value == desired) autoConnectWifiOverride.value = null
            }
        }
    }

    fun setAutoConnectCellular(enabled: Boolean) {
        val currentIntent = autoConnectCellularOverride.value ?: (settings.value?.autoConnectCellular == true)
        if (currentIntent == enabled) return
        autoConnectCellularOverride.value = enabled

        viewModelScope.launch {
            autoConnectWriteMutex.withLock {
                val desired = autoConnectCellularOverride.value ?: return@withLock
                try {
                    if (settings.value?.autoConnectCellular != desired) persistAutoConnectCellular(desired)
                    if (settings.value?.autoConnectCellular != desired) {
                        settings.first { it?.autoConnectCellular == desired }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (autoConnectCellularOverride.value == desired) autoConnectCellularOverride.value = null
                    return@withLock
                }

                if (autoConnectCellularOverride.value == desired) autoConnectCellularOverride.value = null
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
                    persistAutoConnectWifi = store::setAutoConnectWifi,
                    persistAutoConnectCellular = store::setAutoConnectCellular,
                ) as T
            }
        }
    }
}
