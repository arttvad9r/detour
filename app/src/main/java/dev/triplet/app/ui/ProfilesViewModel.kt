package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.core.WarpConfigImporter
import dev.triplet.app.core.WarpImportResult
import dev.triplet.app.core.WarpProfile
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class WarpImportStatus { IDLE, IMPORTING, NO_COMPATIBLE_PROXIES, ERROR }
enum class VlessSaveStatus { IDLE, SAVING, SAVED, ERROR }

internal fun canStartVlessSave(status: VlessSaveStatus): Boolean =
    status == VlessSaveStatus.IDLE || status == VlessSaveStatus.ERROR

sealed interface ProfileDeleteRequest {
    val active: Boolean

    data class Vless(
        val keyId: String,
        override val active: Boolean,
    ) : ProfileDeleteRequest

    data class Warp(
        override val active: Boolean,
    ) : ProfileDeleteRequest
}

sealed interface ProfileSelection {
    data class Vless(val keyId: String) : ProfileSelection
    data object Warp : ProfileSelection
}

data class ProfilesUiState(
    val vlessItems: List<VlessKey> = emptyList(),
    val activeVlessId: String? = null,
    val warpProfile: WarpProfile? = null,
    val activeVpn: VpnProfileKind = VpnProfileKind.VLESS,
    val warpImportStatus: WarpImportStatus = WarpImportStatus.IDLE,
    val vlessSaveStatus: VlessSaveStatus = VlessSaveStatus.IDLE,
)

internal fun persistedProfileSelection(settings: TriSettings?): ProfileSelection? = when (settings?.activeVpn) {
    VpnProfileKind.VLESS -> settings.vlessKeys.activeId?.let(ProfileSelection::Vless)
    VpnProfileKind.WARP -> ProfileSelection.Warp
    null -> null
}

internal fun profilesUiState(
    settings: TriSettings?,
    warpImportStatus: WarpImportStatus = WarpImportStatus.IDLE,
    vlessSaveStatus: VlessSaveStatus = VlessSaveStatus.IDLE,
    selectionOverride: ProfileSelection? = null,
): ProfilesUiState {
    val selection = selectionOverride ?: persistedProfileSelection(settings)
    return ProfilesUiState(
        vlessItems = settings?.vlessKeys?.items.orEmpty(),
        activeVlessId = when (selection) {
            is ProfileSelection.Vless -> selection.keyId
            ProfileSelection.Warp, null -> settings?.vlessKeys?.activeId
        },
        warpProfile = settings?.warpProfile,
        activeVpn = when (selection) {
            is ProfileSelection.Vless -> VpnProfileKind.VLESS
            ProfileSelection.Warp -> VpnProfileKind.WARP
            null -> settings?.activeVpn ?: VpnProfileKind.VLESS
        },
        warpImportStatus = warpImportStatus,
        vlessSaveStatus = vlessSaveStatus,
    )
}

internal fun vlessDeleteRequest(
    settings: TriSettings?,
    keyId: String,
): ProfileDeleteRequest.Vless {
    val state = profilesUiState(settings)
    return ProfileDeleteRequest.Vless(
        keyId = keyId,
        active = state.activeVpn == VpnProfileKind.VLESS && state.activeVlessId == keyId,
    )
}

internal fun warpDeleteRequest(settings: TriSettings?): ProfileDeleteRequest.Warp {
    val state = profilesUiState(settings)
    return ProfileDeleteRequest.Warp(active = state.activeVpn == VpnProfileKind.WARP)
}

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
    private val _warpSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val warpSaved: SharedFlow<Unit> = _warpSaved

    private val vlessSaveStatus = MutableStateFlow(VlessSaveStatus.IDLE)
    private val warpImportStatus = MutableStateFlow(WarpImportStatus.IDLE)
    private val selectionOverride = MutableStateFlow<ProfileSelection?>(null)
    private val selectionWriteMutex = Mutex()
    private val _warpImportRejected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val warpImportRejected: SharedFlow<Unit> = _warpImportRejected

    private val _pendingDelete = MutableStateFlow<ProfileDeleteRequest?>(null)
    val pendingDelete: StateFlow<ProfileDeleteRequest?> = _pendingDelete

    val uiState: StateFlow<ProfilesUiState> = combine(
        settings,
        warpImportStatus,
        vlessSaveStatus,
        selectionOverride,
    ) { currentSettings, currentWarpStatus, currentVlessSaveStatus, currentSelectionOverride ->
        profilesUiState(
            currentSettings,
            currentWarpStatus,
            currentVlessSaveStatus,
            currentSelectionOverride,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = profilesUiState(
            settings.value,
            warpImportStatus.value,
            vlessSaveStatus.value,
            selectionOverride.value,
        ),
    )

    fun saveVless(key: VlessKey, isNew: Boolean) {
        if (!canStartVlessSave(vlessSaveStatus.value)) return
        val state = profilesUiState(settings.value)
        val tunnelAction = vlessMutationTunnelAction(
            state.activeVpn,
            state.activeVlessId,
            key.id,
            deleting = false,
        )
        vlessSaveStatus.value = VlessSaveStatus.SAVING
        viewModelScope.launch {
            try {
                if (isNew) addVlessKey(key) else updateVlessKey(key)
                applyTunnelAction(tunnelAction)
                vlessSaveStatus.value = VlessSaveStatus.SAVED
            } catch (cancelled: CancellationException) {
                vlessSaveStatus.value = VlessSaveStatus.IDLE
                throw cancelled
            } catch (_: Exception) {
                vlessSaveStatus.value = VlessSaveStatus.ERROR
            }
        }
    }

    fun acknowledgeVlessSave() {
        if (vlessSaveStatus.value == VlessSaveStatus.SAVED) {
            vlessSaveStatus.value = VlessSaveStatus.IDLE
        }
    }

    fun clearVlessSaveError() {
        if (vlessSaveStatus.value == VlessSaveStatus.ERROR) {
            vlessSaveStatus.value = VlessSaveStatus.IDLE
        }
    }

    fun deleteVless(keyId: String) {
        _pendingDelete.value = vlessDeleteRequest(settings.value, keyId)
    }

    fun selectVless(keyId: String) {
        selectProfile(ProfileSelection.Vless(keyId))
    }

    fun beginWarpImport(): Boolean {
        if (warpImportStatus.value == WarpImportStatus.IMPORTING) return false
        warpImportStatus.value = WarpImportStatus.IMPORTING
        return true
    }

    fun cancelWarpImport() {
        if (warpImportStatus.value == WarpImportStatus.IMPORTING) {
            warpImportStatus.value = WarpImportStatus.IDLE
        }
    }

    fun reportWarpImportReadError() {
        if (warpImportStatus.value == WarpImportStatus.IMPORTING) {
            failWarpImport(WarpImportStatus.ERROR)
        }
    }

    fun importWarp(raw: String?) {
        if (warpImportStatus.value != WarpImportStatus.IMPORTING) return
        if (raw == null) {
            failWarpImport(WarpImportStatus.ERROR)
            return
        }
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.Default) {
                    WarpConfigImporter.parse(raw)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failWarpImport(WarpImportStatus.ERROR)
                return@launch
            }

            when (result) {
                is WarpImportResult.Ok -> {
                    val tunnelAction = warpMutationTunnelAction(
                        profilesUiState(settings.value).activeVpn,
                        deleting = false,
                    )
                    try {
                        setWarpProfile(result.profile)
                        applyTunnelAction(tunnelAction)
                        warpImportStatus.value = WarpImportStatus.IDLE
                        _warpSaved.emit(Unit)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failWarpImport(WarpImportStatus.ERROR)
                    }
                }
                WarpImportResult.NoCompatibleProxies -> {
                    failWarpImport(WarpImportStatus.NO_COMPATIBLE_PROXIES)
                }
                WarpImportResult.Invalid -> {
                    failWarpImport(WarpImportStatus.ERROR)
                }
            }
        }
    }

    fun deleteWarp() {
        _pendingDelete.value = warpDeleteRequest(settings.value)
    }

    fun dismissDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val request = _pendingDelete.value ?: return
        _pendingDelete.value = null

        when (request) {
            is ProfileDeleteRequest.Vless -> {
                val state = profilesUiState(settings.value)
                val tunnelAction = vlessMutationTunnelAction(
                    state.activeVpn,
                    state.activeVlessId,
                    request.keyId,
                    deleting = true,
                )
                viewModelScope.launch {
                    deleteVlessKey(request.keyId)
                    applyTunnelAction(tunnelAction)
                }
            }
            is ProfileDeleteRequest.Warp -> {
                val tunnelAction = warpMutationTunnelAction(
                    profilesUiState(settings.value).activeVpn,
                    deleting = true,
                )
                viewModelScope.launch {
                    deleteWarpProfile()
                    applyTunnelAction(tunnelAction)
                }
            }
        }
    }

    fun selectWarp() {
        selectProfile(ProfileSelection.Warp)
    }

    private fun selectProfile(selection: ProfileSelection) {
        val currentIntent = selectionOverride.value ?: persistedProfileSelection(settings.value)
        if (currentIntent == selection) return
        selectionOverride.value = selection

        viewModelScope.launch {
            selectionWriteMutex.withLock {
                val desired = selectionOverride.value ?: return@withLock
                try {
                    if (persistedProfileSelection(settings.value) != desired) {
                        when (desired) {
                            is ProfileSelection.Vless -> setActiveVlessKey(desired.keyId)
                            ProfileSelection.Warp -> setActiveVpn(VpnProfileKind.WARP)
                        }
                        if (persistedProfileSelection(settings.value) != desired) {
                            settings.first { persistedProfileSelection(it) == desired }
                        }
                        restartTunnel()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (selectionOverride.value == desired) selectionOverride.value = null
                    return@withLock
                }

                if (selectionOverride.value == desired) selectionOverride.value = null
            }
        }
    }

    private fun failWarpImport(status: WarpImportStatus) {
        warpImportStatus.value = status
        _warpImportRejected.tryEmit(Unit)
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
