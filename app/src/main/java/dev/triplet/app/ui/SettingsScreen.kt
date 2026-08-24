package dev.triplet.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.triplet.app.R
import dev.triplet.app.core.DpiArgs
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.log.ServiceLog
import dev.triplet.app.vpn.HealthCheck
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(store: RoutesStore, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = null)
    val logs by ServiceLog.lines.collectAsState()

    var showKeyDialog by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // ---- Ключ VLESS: свёрнутая карточка, редактирование в диалоге ----
        Text(stringResource(R.string.set_key_title), style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                KeyStatusLine(settings?.vlessUri ?: "")
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = { showKeyDialog = true }) {
                        Text(stringResource(R.string.btn_edit))
                    }
                    if ((settings?.vlessUri ?: "").isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            scope.launch {
                                store.setVlessUri("")
                                VpnController.restartIfActive(ctx)
                            }
                        }) { Text(stringResource(R.string.btn_clear)) }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.set_preset_title), style = MaterialTheme.typography.titleMedium)
        DpiPreset.entries.forEach { preset ->
            Row(Modifier.padding(vertical = 4.dp)) {
                RadioButton(
                    selected = settings?.preset == preset,
                    onClick = {
                        scope.launch {
                            store.setPreset(preset)
                            VpnController.restartIfActive(ctx)
                        }
                    },
                )
                Text(
                    stringResource(presetLabel(preset)),
                    Modifier.padding(top = 12.dp),
                )
            }
        }
        if (settings?.preset == DpiPreset.CUSTOM) {
            var customField by remember(settings?.dpiCustomArgs) {
                mutableStateOf(
                    settings?.dpiCustomArgs?.ifBlank {
                        DpiPreset.COMPATIBLE.args.joinToString(" ")
                    } ?: "",
                )
            }
            OutlinedTextField(
                value = customField,
                onValueChange = { customField = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.custom_args_hint)) },
                minLines = 2,
            )
            Button(
                onClick = {
                    scope.launch {
                        store.setCustomArgs(customField)
                        VpnController.restartIfActive(ctx)
                    }
                },
                enabled = DpiArgs.tokenize(customField).isNotEmpty(),
                modifier = Modifier.padding(top = 4.dp),
            ) { Text(stringResource(R.string.btn_save)) }
        }

        Text(
            stringResource(R.string.settings_autorestart),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.set_diag_title), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = {
            scope.launch(Dispatchers.IO) { // blocking socket IO, not allowed on main thread
                val ok = HealthCheck.generate204(10809) // работает только при активном туннеле
                ServiceLog.i(if (ok) "probe: ok" else "probe: unreachable")
            }
        }) { Text(stringResource(R.string.diag_probe_now)) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                logs.takeLast(20).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    if (showKeyDialog) {
        KeyEditDialog(store = store, onDismiss = { showKeyDialog = false })
    }
}

/** Статус сохранённого ключа: пусто до загрузки хранилища, иначе валидность + сервер. */
@Composable
private fun KeyStatusLine(uri: String) {
    when {
        uri.isEmpty() -> return
        else -> when (val r = VlessKeyParser.parse(uri)) {
            is ParseResult.Ok -> Text(
                stringResource(R.string.key_valid) + " · " +
                    "${r.profile.server}:${r.profile.port}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            is ParseResult.Err -> Text(
                stringResource(R.string.key_invalid),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun presetLabel(p: DpiPreset) = when (p) {
    DpiPreset.RECOMMENDED -> R.string.preset_recommended
    DpiPreset.COMPATIBLE -> R.string.preset_compatible
    DpiPreset.CUSTOM -> R.string.preset_custom
}

@Composable
private fun KeyEditDialog(store: RoutesStore, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = null)
    var uriField by remember(settings?.vlessUri) { mutableStateOf(settings?.vlessUri ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.key_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = uriField,
                    onValueChange = { uriField = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("vless://…") },
                    minLines = 2,
                )
                val parseResult = remember(uriField) {
                    if (uriField.isBlank()) null else VlessKeyParser.parse(uriField)
                }
                if (parseResult != null) {
                    when (parseResult) {
                        is ParseResult.Ok -> Text(
                            stringResource(R.string.key_valid),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        is ParseResult.Err -> Text(
                            stringResource(R.string.key_invalid) + " · " +
                                stringResource(reasonRes(parseResult.reasonResId)),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    store.setVlessUri(uriField.trim())
                    VpnController.restartIfActive(ctx)
                    onDismiss()
                }
            }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

// VlessKeyParser uses raw int constants (not R.string); map them in the UI layer.
private fun reasonRes(r: Int) = when (r) {
    VlessKeyParser.ERR_FORMAT -> R.string.key_invalid_format
    VlessKeyParser.ERR_TRANSPORT -> R.string.key_invalid_transport
    else -> R.string.key_invalid_reality
}
