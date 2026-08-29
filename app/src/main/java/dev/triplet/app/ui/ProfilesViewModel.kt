package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.core.WarpProfile
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val vlessItems: List<VlessKey> = emptyList(),
    val activeVlessId: String? = null,
    val warpProfile: WarpProfile? = null,
    val activeVpn: VpnProfileKind = VpnProfileKind.VLESS,
)

internal fun profilesUiState(settings: TriSettings?): ProfilesUiState = ProfilesUiState(
    vlessItems = settings?.vlessKeys?.items.orEmpty(),
    activeVlessId = settings?.vlessKeys?.activeId,
    warpProfile = settings?.warpProfile,
    activeVpn = settings?.activeVpn ?: VpnProfileKind.VLESS,
)

internal enum class ProfileTunnelAction { NONE, RESTART, STOP }

internal fun vlessMutationTunnelAction(
    activeVpn: VpnProfileKind,
    activeVlessId: String?,
    keyId: String,
    deleting: Boolean,
): ProfileTunnelAction {
    if (activeVpn != VpnProfileKind.VLESS || activeVlessId != keyId) return ProfileTunnelAction.NONE
    return if (deleting) ProfileTunnelAction.STOP else ProfileTunnelAction.RESTART
}

internal fun warpMutationTunnelAction(
    activeVpn: VpnProfileKind,
    deleting: Boolean,
): ProfileTunnelAction {
    if (activeVpn != VpnProfileKind.WARP) return ProfileTunnelAction.NONE
    return if (deleting) ProfileTunnelAction.STOP else ProfileTunnelAction.RESTART
}

class ProfilesViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val addVlessKey: suspend (VlessKey) -> Unit,
    private val updateVlessKey: suspend (VlessKey) -> Unit,
    private val deleteVlessKey: suspend (String) -> Unit,
    private val setActiveVlessKey: suspend (String) -> Unit,
    private val setWarpProfile: suspend (WarpProfile) -> Unit,
    private val deleteWarpProfile: suspend () -> Unit,
    private val setActiveVpn: suspend (VpnProfileKind) -> Unit,
    private val restartTunnel: () -> Unit,
    private val stopTunnelIfRunning: () -> Unit,
) : ViewModel() {
    private val _profileSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val profileSaved: SharedFlow<Unit> = _profileSaved

    val uiState: StateFlow<ProfilesUiState> = settings
        .map(::profilesUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = profilesUiState(settings.value),
        )

    fun saveVless(key: VlessKey, isNew: Boolean) {
        val state = profilesUiState(settings.value)
        val tunnelAction = vlessMutationTunnelAction(
            state.activeVpn,
            state.activeVlessId,
            key.id,
            deleting = false,
        )
        viewModelScope.launch {
            if (isNew) addVlessKey(key) else updateVlessKey(key)
            applyTunnelAction(tunnelAction)
            _profileSaved.emit(Unit)
        }
    }

    fun deleteVless(keyId: String) {
        val state = profilesUiState(settings.value)
        val tunnelAction = vlessMutationTunnelAction(
            state.activeVpn,
            state.activeVlessId,
            keyId,
            deleting = true,
        )
        viewModelScope.launch {
            deleteVlessKey(keyId)
            applyTunnelAction(tunnelAction)
        }
    }

    fun selectVless(keyId: String) {
        val state = profilesUiState(settings.value)
        if (state.activeVpn == VpnProfileKind.VLESS && state.activeVlessId == keyId) return
        viewModelScope.launch {
            setActiveVlessKey(keyId)
            restartTunnel()
        }
    }

    fun replaceWarp(profile: WarpProfile) {
        val tunnelAction = warpMutationTunnelAction(
            profilesUiState(settings.value).activeVpn,
            deleting = false,
        )
        viewModelScope.launch {
            setWarpProfile(profile)
            applyTunnelAction(tunnelAction)
            _profileSaved.emit(Unit)
        }
    }

    fun deleteWarp() {
        val tunnelAction = warpMutationTunnelAction(
            profilesUiState(settings.value).activeVpn,
            deleting = true,
        )
        viewModelScope.launch {
            deleteWarpProfile()
            applyTunnelAction(tunnelAction)
        }
    }

    fun selectWarp() {
        if (profilesUiState(settings.value).activeVpn == VpnProfileKind.WARP) return
        viewModelScope.launch {
            setActiveVpn(VpnProfileKind.WARP)
            restartTunnel()
        }
    }

    private fun applyTunnelAction(action: ProfileTunnelAction) {
        when (action) {
            ProfileTunnelAction.NONE -> Unit
            ProfileTunnelAction.RESTART -> restartTunnel()
            ProfileTunnelAction.STOP -> stopTunnelIfRunning()
        }
    }

    companion object {
        fun factory(
            store: RoutesStore,
            restartTunnel: () -> Unit,
            stopTunnelIfRunning: () -> Unit,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(ProfilesViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return ProfilesViewModel(
                    settings = store.settings,
                    addVlessKey = store::addVlessKey,
                    updateVlessKey = store::updateVlessKey,
                    deleteVlessKey = store::deleteVlessKey,
                    setActiveVlessKey = store::setActiveVlessKey,
                    setWarpProfile = { store.setWarpProfile(it) },
                    deleteWarpProfile = store::deleteWarpProfile,
                    setActiveVpn = store::setActiveVpn,
                    restartTunnel = restartTunnel,
                    stopTunnelIfRunning = stopTunnelIfRunning,
                ) as T
            }
        }
    }
}
