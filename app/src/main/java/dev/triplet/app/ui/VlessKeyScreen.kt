package dev.triplet.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    var field by remember(settings?.vlessUri) { mutableStateOf(settings?.vlessUri ?: "") }

    // Состояние рамки: нейтральная пусто / зелёная корректно / красная ошибка.
    // Рамка: базовый цвет; валидный ввод — зелёная вспышка на секунду;
    // ошибка — красная, пока текст не валиден или не очищен.
    val parse = if (field.isBlank()) null else VlessKeyParser.parse(field)
    var flashGreen by remember { mutableStateOf(false) }
    LaunchedEffect(field) {
        if (parse is ParseResult.Ok) {
            flashGreen = true
            kotlinx.coroutines.delay(1000)
            flashGreen = false
        } else flashGreen = false
    }
    val borderColor = when {
        parse is ParseResult.Err -> Color(0xFFD64545)
        flashGreen -> Color(0xFF1F9D5A)
        else -> MaterialTheme.colorScheme.outline
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.key_title), onBack)

        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp),
            placeholder = { Text("vless://…") },
            minLines = 4,
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
                color = Color(0xFFD64545), fontSize = 12.5.sp,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        }

        Button(
            onClick = {
                scope.launch {
                    store.setVlessUri(field.trim())
                    VpnController.restartIfActive(ctx)
                    onBack()
                }
            },
            enabled = parse is ParseResult.Ok && field.trim() != (settings?.vlessUri ?: ""),
            modifier = Modifier.fillMaxWidth().padding(13.dp),
        ) { Text(stringResource(R.string.btn_save)) }
    }
}

private fun reasonRes(r: Int) = when (r) {
    VlessKeyParser.ERR_FORMAT -> R.string.key_invalid_format
    VlessKeyParser.ERR_TRANSPORT -> R.string.key_invalid_transport
    else -> R.string.key_invalid_reality
}
