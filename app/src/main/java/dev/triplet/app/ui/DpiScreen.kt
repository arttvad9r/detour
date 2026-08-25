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
    val settings by store.settings.collectAsState(initial = null)
    var customField by remember(settings?.dpiCustomArgs) {
        mutableStateOf(settings?.dpiCustomArgs ?: "")
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

        // Один сгруппированный селектор стратегий.
        DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
            RadioRow(
                title = stringResource(R.string.preset_recommended),
                subtitle = stringResource(R.string.nav_dpi_sub),
                selected = settings?.preset == DpiPreset.RECOMMENDED,
                onClick = { choose(DpiPreset.RECOMMENDED) },
            )
            GroupDivider(startInset = 46)
            RadioRow(
                title = stringResource(R.string.preset_custom),
                subtitle = stringResource(R.string.custom_args_hint),
                selected = settings?.preset == DpiPreset.CUSTOM,
                onClick = { choose(DpiPreset.CUSTOM) },
            )
        }

        // Редактор своей стратегии — отдельный раскрывающийся блок, не subtitle.
        if (settings?.preset == DpiPreset.CUSTOM) {
            Spacer(Modifier.height(Spacing.space12))
            OutlinedTextField(
                value = customField,
                onValueChange = { customField = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.space16),
                placeholder = { Text("-s 1+s -d 3+s -a 1", style = MaterialTheme.typography.bodyLarge, color = c.textMuted) },
                shape = AppShapes.small,
                textStyle = MaterialTheme.typography.bodyLarge,
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
                enabled = DpiArgs.tokenize(customField).isNotEmpty(),
                height = 48,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
        }

        Spacer(Modifier.height(Spacing.space12))
        Text(
            stringResource(R.string.autorestart_note),
            style = MaterialTheme.typography.bodySmall,
            color = c.textMuted,
            modifier = Modifier.padding(horizontal = Spacing.space20),
        )
        Spacer(Modifier.height(Spacing.space24))
    }
}
