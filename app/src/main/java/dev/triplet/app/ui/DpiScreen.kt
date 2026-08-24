package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triplet.app.R
import dev.triplet.app.core.DpiArgs
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

@Composable
fun DpiScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = null)

    var customField by remember(settings?.dpiCustomArgs) {
        mutableStateOf(settings?.dpiCustomArgs ?: "")
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.dpi_title), onBack)

        fun choose(preset: DpiPreset) {
            scope.launch {
                store.setPreset(preset)
                VpnController.restartIfActive(ctx)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
            .clickable { choose(DpiPreset.RECOMMENDED) },
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = settings?.preset == DpiPreset.RECOMMENDED,
                onClick = { choose(DpiPreset.RECOMMENDED) })
            Text(stringResource(R.string.preset_recommended), fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
            .clickable { choose(DpiPreset.CUSTOM) },
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = settings?.preset == DpiPreset.CUSTOM,
                onClick = { choose(DpiPreset.CUSTOM) })
            Text(stringResource(R.string.preset_custom), fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        if (settings?.preset == DpiPreset.CUSTOM) {
            OutlinedTextField(
                value = customField,
                onValueChange = { customField = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp),
            )
            Button(
                onClick = {
                    scope.launch {
                        store.setCustomArgs(customField)
                        VpnController.restartIfActive(ctx)
                    }
                },
                enabled = DpiArgs.tokenize(customField).isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(13.dp),
            ) { Text(stringResource(R.string.btn_save)) }
        }

        Text(
            stringResource(R.string.autorestart_note),
            fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
