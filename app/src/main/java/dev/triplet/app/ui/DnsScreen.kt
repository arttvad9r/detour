package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import dev.triplet.app.core.DnsOptions
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

private val DNS_LABELS = mapOf(
    "google" to "Google DNS",
    "cloudflare" to "Cloudflare",
    "adguard" to "AdGuard",
)

@Composable
fun DnsScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState(initial = null)
    // Пустой dnsId означает дефолт (Google) — DnsOptions.resolve резолвит так же.
    val selectedDns = settings?.dnsId?.ifBlank { null } ?: "google"
    var customField by remember(settings?.dnsCustom) { mutableStateOf(settings?.dnsCustom ?: "") }
    val customInvalid = customField.isNotBlank() &&
        !customField.trim().startsWith("http://") && !customField.trim().startsWith("https://")

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(stringResource(R.string.dns_title), onBack)
        Spacer(Modifier.height(Spacing.space8))

        fun apply(id: String, custom: String = settings?.dnsCustom ?: "") {
            scope.launch {
                store.setDns(id, custom)
                VpnController.restartIfActive(ctx)
            }
        }

        // Один сгруппированный селектор вместо отдельных карточек.
        DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
            DnsOptions.servers.forEach { (id, _) ->
                RadioRow(
                    title = DNS_LABELS[id] ?: id,
                    selected = selectedDns == id,
                    onClick = { apply(id) },
                )
                GroupDivider(startInset = 46)
            }
            RadioRow(
                title = stringResource(R.string.dns_custom),
                selected = settings?.dnsId == DnsOptions.CUSTOM,
                onClick = { apply(DnsOptions.CUSTOM, customField.ifBlank { settings?.dnsCustom ?: "" }) },
            )
        }

        if (settings?.dnsId == DnsOptions.CUSTOM) {
            Spacer(Modifier.height(Spacing.space12))
            OutlinedTextField(
                value = customField,
                onValueChange = { customField = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.space16),
                singleLine = true,
                placeholder = { Text("https://…", style = MaterialTheme.typography.bodyLarge, color = c.textMuted) },
                supportingText = if (customInvalid) {
                    { Text(stringResource(R.string.dns_invalid), style = MaterialTheme.typography.bodySmall, color = c.error) }
                } else null,
                isError = customInvalid,
                shape = AppShapes.small,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = fieldColors(),
            )
            Spacer(Modifier.height(Spacing.space12))
            DetourButton(
                text = stringResource(R.string.btn_save),
                onClick = {
                    scope.launch {
                        store.setDns(DnsOptions.CUSTOM, customField.trim())
                        VpnController.restartIfActive(ctx)
                    }
                },
                enabled = customField.isNotBlank() && !customInvalid,
                height = 48,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
        }

        Spacer(Modifier.height(Spacing.space24))
    }
}
