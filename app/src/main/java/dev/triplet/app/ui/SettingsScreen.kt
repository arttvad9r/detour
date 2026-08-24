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

    var uriField by remember(settings?.vlessUri) { mutableStateOf(settings?.vlessUri ?: "") }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.set_key_title), style = MaterialTheme.typography.titleMedium)
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
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = {
                scope.launch {
                    store.setVlessUri(uriField.trim())
                    VpnController.restartIfActive(ctx)
                }
            }) { Text(stringResource(R.string.btn_save)) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                uriField = ""
                scope.launch {
                    store.setVlessUri("")
                    VpnController.restartIfActive(ctx)
                }
            }) { Text(stringResource(R.string.btn_clear)) }
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
                    stringResource(
                        if (preset == DpiPreset.RECOMMENDED) R.string.preset_recommended
                        else R.string.preset_compatible,
                    ),
                    Modifier.padding(top = 12.dp),
                )
            }
        }

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
}

// VlessKeyParser uses raw int constants (not R.string); map them in the UI layer.
private fun reasonRes(r: Int) = when (r) {
    VlessKeyParser.ERR_FORMAT -> R.string.key_invalid_format
    VlessKeyParser.ERR_TRANSPORT -> R.string.key_invalid_transport
    else -> R.string.key_invalid_reality
}
