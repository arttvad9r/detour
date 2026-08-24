package dev.triplet.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triplet.app.R
import dev.triplet.app.core.DnsOptions
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

private val DNS_LABELS = mapOf(
    "google" to "Google DNS",
    "cloudflare" to "Cloudflare",
    "adguard" to "AdGuard · блокирует рекламу",
    "custom" to "Свой адрес",
)

@Composable
fun DnsScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = null)

    var customField by remember(settings?.dnsCustom) { mutableStateOf(settings?.dnsCustom ?: "") }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.dns_title), onBack)

        fun apply(id: String, custom: String = settings?.dnsCustom ?: "") {
            scope.launch {
                store.setDns(id, custom)
                VpnController.restartIfActive(ctx)
            }
        }

        DnsOptions.servers.forEach { (id, _) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable { apply(id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = settings?.dnsId == id, onClick = { apply(id) })
                Text(DNS_LABELS[id] ?: id, fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                .clickable { apply(DnsOptions.CUSTOM, customField.ifBlank { settings?.dnsCustom ?: " " }) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = settings?.dnsId == DnsOptions.CUSTOM,
                onClick = { apply(DnsOptions.CUSTOM, customField.ifBlank { settings?.dnsCustom ?: " " }) })
            Text(stringResource(R.string.dns_custom), fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }

        if (settings?.dnsId == DnsOptions.CUSTOM) {
            OutlinedTextField(
                value = customField,
                onValueChange = { customField = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp),
                singleLine = true,
            )
            Button(
                onClick = {
                    scope.launch {
                        store.setDns(DnsOptions.CUSTOM, customField.trim())
                        VpnController.restartIfActive(ctx)
                    }
                },
                enabled = customField.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(13.dp),
            ) { Text(stringResource(R.string.btn_save)) }
        }

        Text(
            "DNS применяется к маршрутизированным приложениям через движок. " +
                "AdGuard дополнительно режет рекламу и трекеры.",
            fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
