package dev.triplet.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.SettingsBackup
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

@Composable
fun BackupScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = null)

    var status by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val s = settings ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
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
                ),
            )
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                status = ctx.getString(R.string.backup_exported)
            }.onFailure { status = it.message ?: "error" }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText() ?: return@runCatching
                val b = SettingsBackup.fromJson(text) ?: run {
                    status = ctx.getString(R.string.backup_bad_file)
                    return@runCatching
                }
                store.setVlessUri(b.vlessUri)
                store.setPreset(DpiPreset.byId(b.presetId))
                store.setCustomArgs(b.dpiCustomArgs)
                store.setAutoConnect(b.autoConnect)
                store.setTheme(b.themeId)
                store.setDns(b.dnsId, b.dnsCustom)
                // маршруты: применяем только известные маршруты; сброс снятых — не трогаем
                b.routes.forEach { (pkg, route) ->
                    runCatching { store.setRoute(pkg, AppRoute.valueOf(route)) }
                }
                VpnController.restartIfActive(ctx)
                status = ctx.getString(R.string.backup_imported)
            }.onFailure { status = it.message ?: "error" }
        }
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.backup_title), onBack)

        Text(
            stringResource(R.string.backup_note),
            fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Button(
            onClick = { exportLauncher.launch("detour-backup.json") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 6.dp),
        ) { Text(stringResource(R.string.backup_export)) }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp),
        ) { Text(stringResource(R.string.backup_import)) }
        if (status.isNotEmpty()) {
            Text(status, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp))
        }
    }
}
