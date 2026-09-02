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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

enum class HomeProtocol { VLESS_DPI, DPI, VLESS, NONE }

internal data class HomeProfilePresentation(
    val name: String?,
    val server: String?,
    val endpointCount: Int = 0,
)

data class HomeUiState(
    val vpnState: VpnState = VpnState.Idle,
    val sessionStartedAt: Long? = null,
    val profileName: String? = null,
    val serverHost: String? = null,
    val endpointCount: Int = 0,
    val routedCount: Int = 0,
    val activeVpn: VpnProfileKind = VpnProfileKind.VLESS,
    val protocol: HomeProtocol = HomeProtocol.NONE,
    val dnsId: String = "google",
    val dnsCustom: String = "",
)

fun homeProtocol(routes: EffectiveRoutes): HomeProtocol {
    val dpi = routes.dpiPackages.isNotEmpty()
    val vpn = routes.vpnPackages.isNotEmpty()
    return when {
        dpi && vpn -> HomeProtocol.VLESS_DPI
        dpi -> HomeProtocol.DPI
        vpn -> HomeProtocol.VLESS
        else -> HomeProtocol.NONE
    }
}

internal fun homeProfilePresentation(
    activeVpn: VpnProfileKind,
    vlessUri: String,
    warpName: String?,
    warpEndpointCount: Int,
    subscriptionNode: String? = null,
): HomeProfilePresentation = when (activeVpn) {
    VpnProfileKind.VLESS -> {
        val profile = (VlessKeyParser.parse(vlessUri) as? ParseResult.Ok)?.profile
        HomeProfilePresentation(
            name = profile?.name?.ifBlank { profile.server },
            server = profile?.server,
        )
    }
    VpnProfileKind.SUBSCRIPTION -> {
        val profile = (VlessKeyParser.parse(vlessUri) as? ParseResult.Ok)?.profile
        HomeProfilePresentation(
            name = profile?.name?.ifBlank { profile.server },
            server = subscriptionNode?.trim()?.takeIf { it.isNotBlank() },
        )
    }
    VpnProfileKind.WARP -> HomeProfilePresentation(
        name = warpName,
        server = null,
        endpointCount = warpEndpointCount,
    )
}

internal fun homeUiState(
    settings: TriSettings?,
    vpnState: VpnState,
    effectiveRoutes: EffectiveRoutes,
    subscriptionNode: String? = null,
): HomeUiState {
    val activeVpn = settings?.activeVpn ?: VpnProfileKind.VLESS
    val profile = homeProfilePresentation(
        activeVpn = activeVpn,
        vlessUri = settings?.vlessUri.orEmpty(),
        warpName = settings?.warpProfile?.name,
        warpEndpointCount = settings?.warpProfile?.proxies?.size ?: 0,
        subscriptionNode = subscriptionNode,
    )
    return HomeUiState(
        vpnState = vpnState,
        sessionStartedAt = settings?.sessionStartedAt,
        profileName = profile.name,
        serverHost = profile.server,
        endpointCount = profile.endpointCount,
        routedCount = effectiveRoutes.packages.size,
        activeVpn = activeVpn,
        protocol = homeProtocol(effectiveRoutes),
        dnsId = settings?.dnsId?.ifBlank { null } ?: "google",
        dnsCustom = settings?.dnsCustom.orEmpty(),
    )
}

class HomeViewModel(
    settings: StateFlow<TriSettings?>,
    vpnState: StateFlow<VpnState>,
    private val resolveRoutes: suspend (Map<String, AppRoute>) -> EffectiveRoutes,
    private val readSubscriptionNode: suspend () -> String? = { null },
) : ViewModel() {
    private val routeRefresh = MutableStateFlow(0L)

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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedSubscriptionNode = combine(
        settings
            .map { value -> value?.let { it.activeVpn to it.vlessUri } }
            .distinctUntilChanged(),
        routeRefresh,
    ) { profile, _ -> profile }
        .mapLatest { profile ->
            val (kind, uri) = profile ?: return@mapLatest null
            if (kind != VpnProfileKind.SUBSCRIPTION || uri.isBlank()) return@mapLatest null
            runCatching { readSubscriptionNode() }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val uiState: StateFlow<HomeUiState> = combine(
        settings,
        vpnState,
        effectiveRoutes,
        selectedSubscriptionNode,
    ) { currentSettings, currentVpnState, routes, subscriptionNode ->
        homeUiState(
            settings = currentSettings,
            vpnState = currentVpnState,
            effectiveRoutes = routes,
            subscriptionNode = subscriptionNode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun refreshRoutes() {
        routeRefresh.value += 1
    }

    companion object {
        fun factory(
            settings: StateFlow<TriSettings?>,
            vpnState: StateFlow<VpnState>,
            resolveRoutes: suspend (Map<String, AppRoute>) -> EffectiveRoutes,
            readSubscriptionNode: suspend () -> String? = { null },
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(
                    settings = settings,
                    vpnState = vpnState,
                    resolveRoutes = resolveRoutes,
                    readSubscriptionNode = readSubscriptionNode,
                ) as T
            }
        }

        /** Convenience for the Android composition root; the ViewModel itself only receives flows. */
        fun factory(
            store: RoutesStore,
            resolveRoutes: suspend (Map<String, AppRoute>) -> EffectiveRoutes,
            readSubscriptionNode: suspend () -> String? = { null },
        ): ViewModelProvider.Factory = factory(
            settings = store.settings,
            vpnState = VpnController.state,
            resolveRoutes = resolveRoutes,
            readSubscriptionNode = readSubscriptionNode,
        )
    }
}
