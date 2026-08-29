package dev.triplet.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.core.SettingsBackup
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsStateWithLifecycle()
    val exportedText = stringResource(R.string.backup_exported)
    val badFileText = stringResource(R.string.backup_bad_file)
    val importedText = stringResource(R.string.backup_imported_reconnect)
    val genericErrorText = stringResource(R.string.backup_error)

    var status by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    fun showStatus(message: String, error: Boolean) {
        status = message
        statusIsError = error
        haptics.performHapticFeedback(if (error) HapticFeedbackType.Reject else HapticFeedbackType.Confirm)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val s = settings ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val json = SettingsBackup.toJson(
                        SettingsBackup.Backup(
                            vlessUri = s.vlessUri,
                            presetId = s.preset.id,
                            dpiCustomArgs = s.dpiCustomArgs,
                            autoConnect = s.autoConnect,
                            themeId = s.themeId,
                            dnsId = s.dnsId,
                            dnsCustom = s.dnsCustom,
                            routes = s.routes.mapValues { it.value.name },
                            vlessKeys = s.vlessKeys,
                            warpProfile = s.warpProfile,
                            activeVpn = s.activeVpn,
                            showSystemApps = s.showSystemApps,
                        ),
                    )
                    val output = requireNotNull(ctx.contentResolver.openOutputStream(uri))
                    output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                showStatus(exportedText, false)
            }.onFailure { showStatus(genericErrorText, true) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val b = withContext(Dispatchers.IO) {
                    val input = requireNotNull(ctx.contentResolver.openInputStream(uri))
                    input.use { readLimited(it, SettingsBackup.MAX_BYTES) }
                        ?.let(SettingsBackup::fromJson)
                }
                if (b == null) {
                    showStatus(badFileText, true)
                    return@runCatching
                }
                store.restoreBackup(b)
                // Import intentionally disables auto-connect so a restored endpoint
                // is never activated without review. If a tunnel is already live,
                // stop that stale snapshot rather than showing new settings as active.
                if (
                    VpnController.state.value == VpnState.Active ||
                    VpnController.state.value == VpnState.Starting
                ) {
                    VpnController.stop(ctx)
                }
                showStatus(importedText, false)
            }.onFailure { showStatus(genericErrorText, true) }
        }
    }

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .detourHighRefresh(scrollState.isScrollInProgress),
    ) {
        ScreenHeader(stringResource(R.string.backup_title), onBack)
        Spacer(Modifier.height(Spacing.space8))

        Text(
            stringResource(R.string.backup_note),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.space16),
        )
        Spacer(Modifier.height(Spacing.space4))
        Text(
            stringResource(R.string.backup_warning),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4),
        )

        Spacer(Modifier.height(Spacing.space12))
        DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
            ActionRow(stringResource(R.string.backup_export), R.drawable.ic_export, accent = true) {
                exportLauncher.launch("detour-backup.json")
            }
            GroupDivider(startInset = 46)
            ActionRow(stringResource(R.string.backup_import), R.drawable.ic_export, accent = false) {
                importLauncher.launch(arrayOf("application/json", "text/*"))
            }
        }

        AnimatedVisibility(
            visible = status.isNotEmpty(),
            enter = fadeIn(tween(Motion.CONTENT_IN_MS)),
            exit = fadeOut(tween(Motion.CONTENT_OUT_MS)),
        ) {
            Column {
                Spacer(Modifier.height(Spacing.space12))
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusIsError) c.error else c.accent,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
            }
        }
        Spacer(Modifier.height(Spacing.space24))
    }
}

private fun readLimited(input: java.io.InputStream, maxBytes: Int): String? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) return null
        out.write(buffer, 0, count)
    }
    return out.toString(Charsets.UTF_8.name())
}

@Composable
private fun ActionRow(label: String, iconRes: Int, accent: Boolean, onClick: () -> Unit) {
    val c = detourColors
    val tint = if (accent) c.accent else c.textSecondary
    Row(
        Modifier.fillMaxWidth()
            .height(56.dp)
            .detourClickable(
                onClick = onClick,
                role = Role.Button,
                pressedColor = c.surfaceSelected.copy(alpha = 0.38f),
                pressScale = Motion.PRESS_ROW,
            )
            .padding(horizontal = Spacing.space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(iconRes), null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = c.textPrimary,
            modifier = Modifier.padding(start = Spacing.space12),
        )
    }
}
