package dev.triplet.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.triplet.app.R
import dev.triplet.app.core.DnsOptions
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

private val DNS_LABELS = mapOf(
    "google" to R.string.dns_google,
    "cloudflare" to R.string.dns_cloudflare,
    "adguard" to R.string.dns_adguard,
)

@Composable
fun DnsScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState()
    val selectedDns = settings?.dnsId?.ifBlank { null } ?: "google"
    var customField by rememberSaveable(settings?.dnsCustom) { androidx.compose.runtime.mutableStateOf(settings?.dnsCustom ?: "") }
    val customInvalid = customField.isNotBlank() && !DnsOptions.isValid(customField)

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

        DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
            DnsOptions.servers.forEach { (id, _) ->
                RadioRow(
                    title = stringResource(DNS_LABELS[id] ?: R.string.dns_custom),
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
            Spacer(Modifier.height(Spacing.space16))
            DetourInputField(
                value = customField,
                onValueChange = { customField = it },
                label = stringResource(R.string.dns_custom_label),
                placeholder = stringResource(R.string.dns_placeholder),
                helper = stringResource(R.string.dns_custom_hint),
                error = if (customInvalid) stringResource(R.string.dns_invalid) else null,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
            Spacer(Modifier.height(Spacing.space16))
            DetourButton(
                text = stringResource(R.string.btn_save),
                onClick = {
                    scope.launch {
                        store.setDns(DnsOptions.CUSTOM, customField.trim())
                        VpnController.restartIfActive(ctx)
                    }
                },
                enabled = customField.isNotBlank() && !customInvalid,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
        }

        Spacer(Modifier.height(Spacing.space24))
    }
}
