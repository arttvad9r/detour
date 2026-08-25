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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triplet.app.R
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

@Composable
fun VlessKeyScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = null)
    val theme = AppTheme.byId(settings?.themeId ?: "")
    var field by remember(settings?.vlessUri) { mutableStateOf(settings?.vlessUri ?: "") }

    // Состояние рамки: нейтральная пусто / зелёная корректно / красная ошибка.
    // Валидный ввод — зелёная вспышка на секунду; ошибка — красная, пока
    // текст не валиден или не очищен.
    val parse = if (field.isBlank()) null else VlessKeyParser.parse(field)
    var flashGreen by remember { mutableStateOf(false) }
    // Вспышка — только на пользовательский ввод, ставший валидным.
    // При входе на экран (поле уже с сохранённым ключом) рамка не мигает.
    LaunchedEffect(flashGreen) {
        if (flashGreen) {
            kotlinx.coroutines.delay(250)
            flashGreen = false
        }
    }
    val borderColor = when {
        parse is ParseResult.Err -> MaterialTheme.colorScheme.error
        flashGreen -> theme.statusOn.second
        else -> MaterialTheme.colorScheme.outline
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.key_title), onBack)
        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                flashGreen = VlessKeyParser.parse(it) is ParseResult.Ok
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("vless://…") },
            minLines = 4,
            shape = AppShapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = borderColor,
                unfocusedBorderColor = borderColor,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        if (parse is ParseResult.Err) {
            Text(
                stringResource(R.string.key_invalid) + ": " +
                    stringResource(reasonRes(parse.reasonResId)),
                color = MaterialTheme.colorScheme.error, fontSize = 12.5.sp,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp),
            )
        }

        PillButton(
            text = stringResource(R.string.btn_save),
            onClick = {
                scope.launch {
                    store.setVlessUri(field.trim())
                    VpnController.restartIfActive(ctx)
                    onBack()
                }
            },
            enabled = parse is ParseResult.Ok && field.trim() != (settings?.vlessUri ?: ""),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

private fun reasonRes(r: Int) = when (r) {
    VlessKeyParser.ERR_FORMAT -> R.string.key_invalid_format
    VlessKeyParser.ERR_TRANSPORT -> R.string.key_invalid_transport
    else -> R.string.key_invalid_reality
}
