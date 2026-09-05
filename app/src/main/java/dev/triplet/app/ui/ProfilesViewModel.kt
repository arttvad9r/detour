package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.MultiHopEntryRef
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.core.WarpConfigImporter
import dev.triplet.app.core.WarpImportResult
import dev.triplet.app.core.WarpProfile
import dev.triplet.app.core.conflictsWithExit
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

internal fun canStartWarpImport(status: WarpImportStatus): Boolean =
    status != WarpImportStatus.IMPORTING

sealed interface ProfileDeleteRequest {
    val active: Boolean
    val failed: Boolean

    data class Vless(
        val keyId: String,
        override val active: Boolean,
        override val failed: Boolean = false,
    ) : ProfileDeleteRequest

    data class Warp(
        override val active: Boolean,
        override val failed: Boolean = false,
    ) : ProfileDeleteRequest
}

internal fun ProfileDeleteRequest.failedCopy(): ProfileDeleteRequest = when (this) {
    is ProfileDeleteRequest.Vless -> copy(failed = true)
    is ProfileDeleteRequest.Warp -> copy(failed = true)
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
    val multiHopEntry: MultiHopEntryRef? = null,
    val warpImportStatus: WarpImportStatus = WarpImportStatus.IDLE,
    val vlessSaveStatus: VlessSaveStatus = VlessSaveStatus.IDLE,
)

private fun selectedKeyKind(settings: TriSettings?, keyId: String): VpnProfileKind {
    val uri = settings?.vlessKeys?.items?.firstOrNull { it.id == keyId }?.uri
        ?: return VpnProfileKind.VLESS
    val parsed = VlessKeyParser.parse(uri) as? ParseResult.Ok ?: return VpnProfileKind.VLESS
    return if (parsed.profile.isSubscription) VpnProfileKind.SUBSCRIPTION else VpnProfileKind.VLESS
}

internal fun persistedProfileSelection(settings: TriSettings?): ProfileSelection? = when (settings?.activeVpn) {
    VpnProfileKind.VLESS, VpnProfileKind.SUBSCRIPTION ->
        settings.vlessKeys.activeId?.let(ProfileSelection::Vless)
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
            is ProfileSelection.Vless -> selectedKeyKind(settings, selection.keyId)
            ProfileSelection.Warp -> VpnProfileKind.WARP
            null -> settings?.activeVpn ?: VpnProfileKind.VLESS
        },
        multiHopEntry = settings?.multiHopEntry,
        warpImportStatus = warpImportStatus,
        vlessSaveStatus = vlessSaveStatus,
    )
}

internal fun vlessDeleteRequest(
    settings: TriSettings?,
    keyId: String,
    selectionOverride: ProfileSelection? = null,
): ProfileDeleteRequest.Vless {
    val state = profilesUiState(settings, selectionOverride = selectionOverride)
    val isExit = state.activeVpn != VpnProfileKind.WARP && state.activeVlessId == keyId
    val isEntry = (state.multiHopEntry as? MultiHopEntryRef.Vless)?.keyId == keyId
    return ProfileDeleteRequest.Vless(
        keyId = keyId,
        active = isExit || isEntry,
    )
}

internal fun warpDeleteRequest(
    settings: TriSettings?,
    selectionOverride: ProfileSelection? = null,
): ProfileDeleteRequest.Warp {
    val state = profilesUiState(settings, selectionOverride = selectionOverride)
    return ProfileDeleteRequest.Warp(
        active = state.activeVpn == VpnProfileKind.WARP || state.multiHopEntry == MultiHopEntryRef.Warp,
    )
}

internal enum class ProfileTunnelAction { NONE, RESTART, STOP }

internal fun vlessMutationTunnelAction(
    activeVpn: VpnProfileKind,
    activeVlessId: String?,
    keyId: String,
    deleting: Boolean,
    multiHopEntry: MultiHopEntryRef? = null,
): ProfileTunnelAction {
    val isExit = activeVpn != VpnProfileKind.WARP && activeVlessId == keyId
    val isEntry = (multiHopEntry as? MultiHopEntryRef.Vless)?.keyId == keyId
    if (!isExit && !isEntry) return ProfileTunnelAction.NONE
    return if (deleting) ProfileTunnelAction.STOP else ProfileTunnelAction.RESTART
}

internal fun warpMutationTunnelAction(
    activeVpn: VpnProfileKind,
    deleting: Boolean,
    multiHopEntry: MultiHopEntryRef? = null,
): ProfileTunnelAction {
    val isExit = activeVpn == VpnProfileKind.WARP
    val isEntry = multiHopEntry == MultiHopEntryRef.Warp
    if (!isExit && !isEntry) return ProfileTunnelAction.NONE
    return if (deleting) ProfileTunnelAction.STOP else ProfileTunnelAction.RESTART
}

internal fun MultiHopEntryRef?.conflictsWithSelection(selection: ProfileSelection): Boolean = when (selection) {
    is ProfileSelection.Vless ->
        this is MultiHopEntryRef.Vless && keyId == selection.keyId
    ProfileSelection.Warp -> this == MultiHopEntryRef.Warp
}

class ProfilesViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val addVlessKey: suspend (VlessKey) -> Unit,
    private val updateVlessKey: suspend (VlessKey) -> Unit,
    private val deleteVlessKey: suspend (String) -> Unit,
    private val setActiveVlessKey: suspend (String) -> Unit,
    private val loadWarpConfig: suspend (String) -> String?,
    private val setWarpProfile: suspend (WarpProfile) -> Unit,
    private val deleteWarpProfile: suspend () -> Unit,
    private val setActiveVpn: suspend (VpnProfileKind) -> Unit,
    private val setMultiHopEntry: suspend (MultiHopEntryRef?) -> Unit,
    private val restartTunnel: () -> Unit,
    private val stopTunnelIfRunning: () -> Unit,
) : ViewModel() {
    private val _warpSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val warpSaved: SharedFlow<Unit> = _warpSaved

    private val vlessSaveStatus = MutableStateFlow(VlessSaveStatus.IDLE)
    private val warpImportStatus = MutableStateFlow(WarpImportStatus.IDLE)
    private val selectionOverride = MutableStateFlow<ProfileSelection?>(null)
    private val profileMutationMutex = Mutex()
    private val deleteInFlight = MutableStateFlow(false)
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
        vlessSaveStatus.value = VlessSaveStatus.SAVING
        viewModelScope.launch {
            profileMutationMutex.withLock {
                try {
                    val state = profilesUiState(settings.value)
                    val tunnelAction = vlessMutationTunnelAction(
                        activeVpn = state.activeVpn,
                        activeVlessId = state.activeVlessId,
                        keyId = key.id,
                        deleting = false,
                        multiHopEntry = state.multiHopEntry,
                    )
                    if (!isNew && settings.value?.vlessKeys?.items?.none { it.id == key.id } != false) {
                        throw IllegalStateException("VLESS profile no longer exists")
                    }
                    if (isNew) addVlessKey(key) else updateVlessKey(key)
                    if (settings.value?.vlessKeys?.items?.none { it == key } != false) {
                        settings.first { current -> current?.vlessKeys?.items?.any { it == key } == true }
                    }
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
        if (deleteInFlight.value) return
        _pendingDelete.value = vlessDeleteRequest(settings.value, keyId, selectionOverride.value)
    }

    fun selectVless(keyId: String) {
        selectProfile(ProfileSelection.Vless(keyId))
    }

    fun selectMultiHopEntry(entry: MultiHopEntryRef?) {
        if (entry is MultiHopEntryRef.Invalid) return
        viewModelScope.launch {
            profileMutationMutex.withLock {
                val state = profilesUiState(settings.value)
                if (entry?.conflictsWithExit(state.activeVpn, state.activeVlessId) == true) return@withLock
                if (settings.value?.multiHopEntry == entry) return@withLock
                setMultiHopEntry(entry)
                if (settings.value?.multiHopEntry != entry) {
                    settings.first { it?.multiHopEntry == entry }
                }
                restartTunnel()
            }
        }
    }

    fun importWarpDocument(uri: String) {
        if (!canStartWarpImport(warpImportStatus.value)) return
        warpImportStatus.value = WarpImportStatus.IMPORTING
        viewModelScope.launch {
            try {
                val raw = loadWarpConfig(uri)
                if (raw == null) {
                    failWarpImport(WarpImportStatus.ERROR)
                    return@launch
                }
                when (val result = withContext(Dispatchers.Default) { WarpConfigImporter.parse(raw) }) {
                    is WarpImportResult.Ok -> {
                        profileMutationMutex.withLock {
                            val state = profilesUiState(settings.value)
                            val tunnelAction = warpMutationTunnelAction(
                                activeVpn = state.activeVpn,
                                deleting = false,
                                multiHopEntry = state.multiHopEntry,
                            )
                            setWarpProfile(result.profile)
                            if (settings.value?.warpProfile != result.profile) {
                                settings.first { it?.warpProfile == result.profile }
                            }
                            applyTunnelAction(tunnelAction)
                            warpImportStatus.value = WarpImportStatus.IDLE
                            _warpSaved.emit(Unit)
                        }
                    }
                    WarpImportResult.NoCompatibleProxies -> {
                        failWarpImport(WarpImportStatus.NO_COMPATIBLE_PROXIES)
                    }
                    WarpImportResult.Invalid -> {
                        failWarpImport(WarpImportStatus.ERROR)
                    }
                }
            } catch (cancelled: CancellationException) {
                if (warpImportStatus.value == WarpImportStatus.IMPORTING) {
                    warpImportStatus.value = WarpImportStatus.IDLE
                }
                throw cancelled
            } catch (_: Exception) {
                failWarpImport(WarpImportStatus.ERROR)
            }
        }
    }

    fun deleteWarp() {
        if (deleteInFlight.value) return
        _pendingDelete.value = warpDeleteRequest(settings.value, selectionOverride.value)
    }

    fun dismissDelete() {
        if (!deleteInFlight.value) _pendingDelete.value = null
    }

    fun confirmDelete() {
        val request = _pendingDelete.value ?: return
        if (deleteInFlight.value) return
        deleteInFlight.value = true
        _pendingDelete.value = null

        viewModelScope.launch {
            try {
                profileMutationMutex.withLock {
                    when (request) {
                        is ProfileDeleteRequest.Vless -> {
                            val state = profilesUiState(settings.value)
                            val deletingEntry =
                                (state.multiHopEntry as? MultiHopEntryRef.Vless)?.keyId == request.keyId
                            val tunnelAction = vlessMutationTunnelAction(
                                activeVpn = state.activeVpn,
                                activeVlessId = state.activeVlessId,
                                keyId = request.keyId,
                                deleting = true,
                                multiHopEntry = state.multiHopEntry,
                            )
                            if (deletingEntry) setMultiHopEntry(null)
                            deleteVlessKey(request.keyId)
                            if (
                                settings.value?.vlessKeys?.items?.any { it.id == request.keyId } == true ||
                                (deletingEntry && settings.value?.multiHopEntry != null)
                            ) {
                                settings.first { current ->
                                    current?.vlessKeys?.items?.none { it.id == request.keyId } != false &&
                                        (!deletingEntry || current?.multiHopEntry == null)
                                }
                            }
                            applyTunnelAction(tunnelAction)
                        }
                        is ProfileDeleteRequest.Warp -> {
                            val state = profilesUiState(settings.value)
                            val deletingEntry = state.multiHopEntry == MultiHopEntryRef.Warp
                            val tunnelAction = warpMutationTunnelAction(
                                activeVpn = state.activeVpn,
                                deleting = true,
                                multiHopEntry = state.multiHopEntry,
                            )
                            if (deletingEntry) setMultiHopEntry(null)
                            deleteWarpProfile()
                            if (settings.value?.warpProfile != null || (deletingEntry && settings.value?.multiHopEntry != null)) {
                                settings.first {
                                    it?.warpProfile == null && (!deletingEntry || it?.multiHopEntry == null)
                                }
                            }
                            applyTunnelAction(tunnelAction)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _pendingDelete.value = request.failedCopy()
            } finally {
                deleteInFlight.value = false
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
            profileMutationMutex.withLock {
                val desired = selectionOverride.value ?: return@withLock
                try {
                    if (persistedProfileSelection(settings.value) != desired) {
                        if (settings.value?.multiHopEntry.conflictsWithSelection(desired)) {
                            setMultiHopEntry(null)
                            if (settings.value?.multiHopEntry != null) {
                                settings.first { it?.multiHopEntry == null }
                            }
                        }
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
            loadWarpConfig: suspend (String) -> String?,
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
                    loadWarpConfig = loadWarpConfig,
                    setWarpProfile = { store.setWarpProfile(it) },
                    deleteWarpProfile = store::deleteWarpProfile,
                    setActiveVpn = store::setActiveVpn,
                    setMultiHopEntry = store::setMultiHopEntry,
                    restartTunnel = restartTunnel,
                    stopTunnelIfRunning = stopTunnelIfRunning,
                ) as T
            }
        }
    }
}