package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.core.SettingsBackup
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BackupStatus { EXPORTED, BAD_FILE, IMPORTED, ERROR }
enum class BackupFeedback { CONFIRM, REJECT }

internal fun backupFromSettings(settings: TriSettings): SettingsBackup.Backup = SettingsBackup.Backup(
    vlessUri = settings.vlessUri,
    presetId = settings.preset.id,
    dpiCustomArgs = settings.dpiCustomArgs,
    autoConnect = settings.autoConnect,
    themeId = settings.themeId,
    dnsId = settings.dnsId,
    dnsCustom = settings.dnsCustom,
    routes = settings.routes.mapValues { it.value.name },
    vlessKeys = settings.vlessKeys,
    warpProfile = settings.warpProfile,
    activeVpn = settings.activeVpn,
    showSystemApps = settings.showSystemApps,
)

class BackupViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val restoreBackup: suspend (SettingsBackup.Backup) -> Unit,
    private val stopTunnelIfRunning: () -> Unit,
) : ViewModel() {
    private val _status = MutableStateFlow<BackupStatus?>(null)
    val status: StateFlow<BackupStatus?> = _status.asStateFlow()

    private val _feedback = MutableSharedFlow<BackupFeedback>(extraBufferCapacity = 1)
    val feedback: SharedFlow<BackupFeedback> = _feedback

    fun exportJson(): String? = settings.value?.let {
        SettingsBackup.toJson(backupFromSettings(it))
    }

    fun reportExport(success: Boolean) {
        setStatus(if (success) BackupStatus.EXPORTED else BackupStatus.ERROR)
    }

    fun importJson(raw: String?) {
        if (raw == null) {
            setStatus(BackupStatus.BAD_FILE)
            return
        }
        val backup = SettingsBackup.fromJson(raw)
        if (backup == null) {
            setStatus(BackupStatus.BAD_FILE)
            return
        }
        viewModelScope.launch {
            runCatching {
                restoreBackup(backup)
                // Restoring intentionally disables auto-connect. Any live tunnel
                // still represents the old snapshot, so stop it after commit.
                stopTunnelIfRunning()
            }.onSuccess {
                setStatus(BackupStatus.IMPORTED)
            }.onFailure {
                setStatus(BackupStatus.ERROR)
            }
        }
    }

    fun reportError() {
        setStatus(BackupStatus.ERROR)
    }

    private fun setStatus(value: BackupStatus) {
        _status.value = value
        _feedback.tryEmit(
            if (value == BackupStatus.EXPORTED || value == BackupStatus.IMPORTED) {
                BackupFeedback.CONFIRM
            } else {
                BackupFeedback.REJECT
            },
        )
    }

    companion object {
        fun factory(
            store: RoutesStore,
            stopTunnelIfRunning: () -> Unit,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(BackupViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return BackupViewModel(
                    settings = store.settings,
                    restoreBackup = store::restoreBackup,
                    stopTunnelIfRunning = stopTunnelIfRunning,
                ) as T
            }
        }
    }
}
