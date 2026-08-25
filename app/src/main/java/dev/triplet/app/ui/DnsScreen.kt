package dev.triplet.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
)

@Composable
fun DnsScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = null)
    // Пустой dnsId означает дефолт (Google) — DnsOptions.resolve резолвит так же.
    val selectedDns = settings?.dnsId?.ifBlank { null } ?: "google"

    var customField by remember(settings?.dnsCustom) { mutableStateOf(settings?.dnsCustom ?: "") }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.dns_title), onBack)
        Spacer(Modifier.height(6.dp))

        fun apply(id: String, custom: String = settings?.dnsCustom ?: "") {
            scope.launch {
                store.setDns(id, custom)
                VpnController.restartIfActive(ctx)
            }
        }

        DnsOptions.servers.forEach { (id, addr) ->
            OptionRow(
                title = DNS_LABELS[id] ?: id,
                subtitle = addr,
                selected = selectedDns == id,
                onClick = { apply(id) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
            )
        }
        OptionRow(
            title = stringResource(R.string.dns_custom),
            selected = settings?.dnsId == DnsOptions.CUSTOM,
            onClick = { apply(DnsOptions.CUSTOM, customField.ifBlank { settings?.dnsCustom ?: " " }) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        )

        if (settings?.dnsId == DnsOptions.CUSTOM) {
            OutlinedTextField(
                value = customField,
                onValueChange = { customField = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                shape = AppShapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            PillButton(
                text = stringResource(R.string.btn_save),
                onClick = {
                    scope.launch {
                        store.setDns(DnsOptions.CUSTOM, customField.trim())
                        VpnController.restartIfActive(ctx)
                    }
                },
                enabled = customField.isNotBlank(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "DNS применяется к маршрутизированным приложениям через движок. " +
                "AdGuard дополнительно режет рекламу и трекеры.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
