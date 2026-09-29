package dev.detour.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.detour.app.core.BackupCrypto
import dev.detour.app.core.SettingsBackup
import dev.detour.app.data.RoutesStore
import dev.detour.app.data.TriSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BackupStatus { EXPORTED, BAD_FILE, IMPORTED, ERROR, WRONG_PASSWORD }
enum class BackupFeedback { CONFIRM, REJECT }
enum class BackupOperation { EXPORT, IMPORT }

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
    wireGuardProfiles = settings.wireGuardProfiles,
    showSystemApps = settings.showSystemApps,
)

class BackupViewModel(
    private val loadSettings: suspend () -> TriSettings?,
    private val readBackupDocument: suspend (String) -> String?,
    private val writeBackupDocument: suspend (String, String) -> Unit,
    private val restoreBackup: suspend (SettingsBackup.Backup) -> Unit,
    private val stopTunnelIfRunning: () -> Unit,
) : ViewModel() {
    private val _status = MutableStateFlow<BackupStatus?>(null)
    val status: StateFlow<BackupStatus?> = _status.asStateFlow()

    private val _operation = MutableStateFlow<BackupOperation?>(null)
    val operation: StateFlow<BackupOperation?> = _operation.asStateFlow()

    private val _feedback = MutableSharedFlow<BackupFeedback>(extraBufferCapacity = 1)
    val feedback: SharedFlow<BackupFeedback> = _feedback

    private val _pendingImportUri = MutableStateFlow<String?>(null)
    /** Set while an encrypted file waits for its password. */
    val pendingImportUri: StateFlow<String?> = _pendingImportUri.asStateFlow()

    // Chosen before the system file picker opens. Kept in memory only, and an
    // export without a prepared choice fails instead of silently writing plain text.
    private var exportPrepared = false
    private var exportPassword: CharArray? = null

    fun prepareExport(password: CharArray?) {
        exportPassword?.fill('\u0000')
        exportPassword = password?.takeIf { it.isNotEmpty() }
        exportPrepared = true
    }

    fun discardPreparedExport() {
        exportPassword?.fill('\u0000')
        exportPassword = null
        exportPrepared = false
    }

    fun dismissPendingImport() {
        _pendingImportUri.value = null
        if (_status.value == BackupStatus.WRONG_PASSWORD) _status.value = null
    }

    fun exportDocument(uri: String) {
        if (!beginExport()) return
        val prepared = exportPrepared
        val password = exportPassword
        exportPrepared = false
        exportPassword = null
        viewModelScope.launch {
            try {
                if (!prepared) {
                    reportError()
                    return@launch
                }
                val json = exportJson()
                if (json == null) {
                    reportError()
                    return@launch
                }
                val document = if (password == null) {
                    json
                } else {
                    withContext(Dispatchers.Default) { BackupCrypto.encrypt(json, password) }
                }
                writeBackupDocument(uri, document)
                reportExport(success = true)
            } catch (cancelled: CancellationException) {
                cancelOperation(BackupOperation.EXPORT)
                throw cancelled
            } catch (_: Exception) {
                reportExport(success = false)
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    fun importDocument(uri: String, password: CharArray? = null) {
        if (!beginImport()) {
            password?.fill('\u0000')
            return
        }
        viewModelScope.launch {
            try {
                val raw = readBackupDocument(uri)
                if (raw == null) {
                    complete(BackupStatus.BAD_FILE)
                    return@launch
                }
                val json = if (BackupCrypto.isEncrypted(raw)) {
                    if (password == null || password.isEmpty()) {
                        _pendingImportUri.value = uri
                        _operation.value = null
                        return@launch
                    }
                    when (val result = withContext(Dispatchers.Default) { BackupCrypto.decrypt(raw, password) }) {
                        is BackupCrypto.Decrypted.Plain -> result.json
                        BackupCrypto.Decrypted.WrongPassword -> {
                            _pendingImportUri.value = uri
                            complete(BackupStatus.WRONG_PASSWORD)
                            return@launch
                        }
                        BackupCrypto.Decrypted.Invalid -> {
                            _pendingImportUri.value = null
                            complete(BackupStatus.BAD_FILE)
                            return@launch
                        }
                    }
                } else {
                    raw
                }
                _pendingImportUri.value = null
                val backup = withContext(Dispatchers.Default) {
                    SettingsBackup.fromJson(json)
                }
                if (backup == null) {
                    complete(BackupStatus.BAD_FILE)
                    return@launch
                }
                restoreBackup(backup)
                // Restoring intentionally disables auto-connect. Any live tunnel
                // still represents the old snapshot, so stop it after commit.
                stopTunnelIfRunning()
                complete(BackupStatus.IMPORTED)
            } catch (cancelled: CancellationException) {
                cancelOperation(BackupOperation.IMPORT)
                throw cancelled
            } catch (_: Exception) {
                reportError()
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    internal fun beginExport(): Boolean = beginOperation(BackupOperation.EXPORT)

    internal fun beginImport(): Boolean = beginOperation(BackupOperation.IMPORT)

    internal fun cancelOperation(value: BackupOperation) {
        if (_operation.value == value) _operation.value = null
    }

    internal suspend fun exportJson(): String? = try {
        val current = loadSettings() ?: return null
        withContext(Dispatchers.Default) {
            SettingsBackup.toJson(backupFromSettings(current))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    internal fun reportExport(success: Boolean) {
        complete(if (success) BackupStatus.EXPORTED else BackupStatus.ERROR)
    }

    internal fun reportError() {
        complete(BackupStatus.ERROR)
    }

    private fun beginOperation(value: BackupOperation): Boolean {
        if (_operation.value != null) return false
        _status.value = null
        _operation.value = value
        return true
    }

    private fun complete(value: BackupStatus) {
        _operation.value = null
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
            readBackupDocument: suspend (String) -> String?,
            writeBackupDocument: suspend (String, String) -> Unit,
            stopTunnelIfRunning: () -> Unit,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(BackupViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return BackupViewModel(
                    loadSettings = { store.snapshot() },
                    readBackupDocument = readBackupDocument,
                    writeBackupDocument = writeBackupDocument,
                    restoreBackup = store::restoreBackup,
                    stopTunnelIfRunning = stopTunnelIfRunning,
                ) as T
            }
        }
    }
}
