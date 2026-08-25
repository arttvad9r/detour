package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import dev.triplet.app.R
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch

/**
 * Ключ VLESS: по умолчанию — сводка (сервер/security/flow) и маскированный
 * ключ; raw URI показывается только по «Показать», редактируется в отдельном
 * режиме. Формат хранения не меняется.
 */
@Composable
fun VlessKeyScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState(initial = null)
    val savedUri = settings?.vlessUri ?: ""

    var editing by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var field by remember(savedUri) { mutableStateOf(savedUri) }

    val parse = if (field.isBlank()) null else VlessKeyParser.parse(field)
    val profile = (VlessKeyParser.parse(savedUri) as? ParseResult.Ok)?.profile

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(stringResource(R.string.key_title), onBack)
        Spacer(Modifier.height(Spacing.space8))

        if (!editing) {
            // Сводка по сохранённому ключу.
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                SummaryRow(stringResource(R.string.key_row_server),
                    profile?.let { "${it.server}:${it.port}" } ?: stringResource(R.string.server_missing))
                GroupDivider(startInset = 16)
                SummaryRow(stringResource(R.string.key_row_security), "Reality")
                GroupDivider(startInset = 16)
                SummaryRow(stringResource(R.string.key_row_flow), profile?.flow?.ifBlank { null } ?: "—")
                GroupDivider(startInset = 16)
                SummaryRow("SNI", profile?.sni?.ifBlank { null } ?: "—")
            }

            Spacer(Modifier.height(Spacing.space16))
            Text(
                stringResource(R.string.key_config_label),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.space20),
            )
            Spacer(Modifier.height(Spacing.space8))
            if (revealed) {
                SelectionContainer(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = Spacing.space16)
                        .heightIn(max = 160.dp)
                        .background(c.surface, AppShapes.small)
                        .border(1.dp, c.border, AppShapes.small)
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.space12),
                ) {
                    Text(
                        savedUri,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = c.textSecondary,
                    )
                }
            } else {
                VlessMask(Modifier.padding(horizontal = Spacing.space20))
            }

            Spacer(Modifier.height(Spacing.space16))
            Row(Modifier.padding(horizontal = Spacing.space16)) {
                DetourButton(
                    text = stringResource(if (revealed) R.string.key_hide else R.string.key_show),
                    onClick = { revealed = !revealed },
                    style = ButtonStyle.SECONDARY,
                    height = 44,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.space12))
                DetourButton(
                    text = stringResource(R.string.key_edit),
                    onClick = {
                        field = savedUri
                        editing = true
                        revealed = false
                    },
                    height = 44,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            // Режим редактирования: multiline-поле + буфер обмена.
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.space16),
                placeholder = { Text("vless://…", style = MaterialTheme.typography.bodyMedium, color = c.textMuted) },
                minLines = 4,
                shape = AppShapes.small,
                textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp),
                colors = fieldColors(),
            )
            if (parse is ParseResult.Err) {
                Text(
                    stringResource(R.string.key_invalid) + ": " +
                        stringResource(reasonRes(parse.reasonResId)),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.error,
                    modifier = Modifier.padding(start = Spacing.space20, top = Spacing.space8),
                )
            }
            Spacer(Modifier.height(Spacing.space4))
            val clipboard = LocalClipboardManager.current
            TextButton(
                onClick = {
                    clipboard.getText()?.toString()?.let { field = it.trim() }
                },
                modifier = Modifier.padding(start = Spacing.space8),
            ) {
                Text(stringResource(R.string.key_paste), style = MaterialTheme.typography.labelMedium, color = c.accent)
            }
            Spacer(Modifier.height(Spacing.space8))
            DetourButton(
                text = stringResource(R.string.btn_save),
                onClick = {
                    scope.launch {
                        store.setVlessUri(field.trim())
                        VpnController.restartIfActive(ctx)
                        editing = false
                    }
                },
                enabled = parse is ParseResult.Ok && field.trim() != savedUri,
                height = 48,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
            Spacer(Modifier.height(Spacing.space4))
            TextButton(
                onClick = {
                    field = savedUri
                    editing = false
                },
                modifier = Modifier.padding(start = Spacing.space8),
            ) {
                Text(stringResource(R.string.key_cancel), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(Spacing.space24))
    }
}

@Composable
private fun VlessMask(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        repeat(14) {
            Box(Modifier.size(5.dp).background(detourColors.textMuted, androidx.compose.foundation.shape.CircleShape))
        }
    }
}

/** Строка сводки: подпись + значение. */
@Composable
private fun SummaryRow(label: String, value: String) {
    val c = detourColors
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.space16, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

private fun reasonRes(r: Int) = when (r) {
    VlessKeyParser.ERR_FORMAT -> R.string.key_invalid_format
    VlessKeyParser.ERR_TRANSPORT -> R.string.key_invalid_transport
    else -> R.string.key_invalid_reality
}
