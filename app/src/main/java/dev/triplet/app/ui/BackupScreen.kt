package dev.triplet.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.triplet.app.R
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.SettingsBackup
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

/** Экспорт/импорт: компактные строки-действия вместо двух огромных CTA. */
@Composable
fun BackupScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState(initial = null)

    var status by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }

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
                status = ctx.getString(R.string.backup_exported); statusIsError = false
            }.onFailure { status = it.message ?: "error"; statusIsError = true }
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
                    status = ctx.getString(R.string.backup_bad_file); statusIsError = true
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
                status = ctx.getString(R.string.backup_imported); statusIsError = false
            }.onFailure { status = it.message ?: "error"; statusIsError = true }
        }
    }

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(stringResource(R.string.backup_title), onBack)
        Spacer(Modifier.height(Spacing.space8))

        Text(
            stringResource(R.string.backup_note),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.space20),
        )
        Spacer(Modifier.height(Spacing.space4))
        Text(
            stringResource(R.string.backup_warning),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space4),
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

        if (status.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.space12))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusIsError) c.error else c.active,
                modifier = Modifier.padding(horizontal = Spacing.space20),
            )
        }
        Spacer(Modifier.height(Spacing.space24))
    }
}

/** Строка-действие 52dp: иконка + подпись. */
@Composable
private fun ActionRow(label: String, iconRes: Int, accent: Boolean, onClick: () -> Unit) {
    val c = detourColors
    val tint = if (accent) c.accent else c.textSecondary
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.space16, vertical = 14.dp),
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
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
