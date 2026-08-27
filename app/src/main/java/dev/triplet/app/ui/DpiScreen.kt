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
import androidx.compose.material3.OutlinedTextField
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
import dev.triplet.app.core.DpiArgs
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

@Composable
fun DpiScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState()
    var customField by rememberSaveable(settings?.dpiCustomArgs) {
        androidx.compose.runtime.mutableStateOf(settings?.dpiCustomArgs ?: "")
    }

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(stringResource(R.string.dpi_title), onBack)
        Spacer(Modifier.height(Spacing.space8))

        fun choose(preset: DpiPreset) {
            scope.launch {
                store.setPreset(preset)
                VpnController.restartIfActive(ctx)
            }
        }

        DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
            RadioRow(
                title = stringResource(R.string.preset_recommended),
                selected = settings?.preset == DpiPreset.RECOMMENDED,
                onClick = { choose(DpiPreset.RECOMMENDED) },
            )
            GroupDivider(startInset = 46)
            RadioRow(
                title = stringResource(R.string.preset_custom),
                selected = settings?.preset == DpiPreset.CUSTOM,
                onClick = { choose(DpiPreset.CUSTOM) },
            )
        }

        if (settings?.preset == DpiPreset.CUSTOM) {
            Spacer(Modifier.height(Spacing.space12))
            OutlinedTextField(
                value = customField,
                onValueChange = { customField = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.space16),
                shape = AppShapes.small,
                textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                colors = fieldColors(),
            )
            Spacer(Modifier.height(Spacing.space12))
            DetourButton(
                text = stringResource(R.string.btn_save),
                onClick = {
                    scope.launch {
                        store.setCustomArgs(customField)
                        VpnController.restartIfActive(ctx)
                    }
                },
                enabled = DpiArgs.isValid(customField),
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
        }

        Spacer(Modifier.height(Spacing.space24))
    }
}
