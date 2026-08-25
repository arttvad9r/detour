package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triplet.app.R
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.foundation.text.BasicTextField

@Composable
fun VlessKeyScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState(initial = null)
    val keys = settings?.vlessKeys
    val active = keys?.active

    var editingId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    val parse = field.takeIf { it.isNotBlank() }?.let(VlessKeyParser::parse)

    fun beginEdit(key: VlessKey?) {
        editing = true
        editingId = key?.id
        field = key?.uri ?: ""
        revealed = false
    }

    Box(modifier.fillMaxSize().background(c.background)) {
      Column(
        Modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(stringResource(R.string.key_title), onBack)
        Spacer(Modifier.height(Spacing.space8))

        if (!editing) {
            Text(
                stringResource(R.string.key_list_title),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.space20),
            )
            Spacer(Modifier.height(Spacing.space8))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                keys?.items.orEmpty().forEachIndexed { index, key ->
                    KeyRow(key, key.id == keys?.activeId, onEdit = { beginEdit(key) }, onDelete = {
                        scope.launch {
                            store.deleteVlessKey(key.id)
                            VpnController.restartIfActive(ctx)
                        }
                    }) {
                        scope.launch {
                            store.setActiveVlessKey(key.id)
                            VpnController.restartIfActive(ctx)
                        }
                    }
                    if (index < keys?.items.orEmpty().lastIndex) GroupDivider(startInset = 16)
                }
                if (keys?.items.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.key_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textMuted,
                        modifier = Modifier.padding(Spacing.space16),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.space8))
        } else {
            Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.space16)) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 160.dp)
                        .border(1.dp, c.border, AppShapes.small),
                ) {
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp).padding(top = 12.dp),
                        minLines = 4,
                        textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                    )
                }
                Text(
                    stringResource(R.string.key_uri),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.align(Alignment.TopStart)
                        .offset(x = 12.dp, y = (-9).dp)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .background(c.background),
                )
            }
            if (parse is ParseResult.Err) {
                Text(stringResource(R.string.key_invalid), style = MaterialTheme.typography.bodySmall, color = c.error, modifier = Modifier.padding(start = Spacing.space20, top = Spacing.space8))
            }
            val clipboard = LocalClipboardManager.current
            TextButton(onClick = { clipboard.getText()?.toString()?.let { field = it.trim() } }, modifier = Modifier.padding(start = Spacing.space8)) {
                Text(stringResource(R.string.key_paste))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.space16),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
            ) {
                DetourButton(
                    text = stringResource(R.string.key_cancel),
                    onClick = { editing = false },
                    style = ButtonStyle.SECONDARY,
                    height = 48,
                    modifier = Modifier.weight(1f),
                )
                DetourButton(
                    text = stringResource(R.string.btn_save),
                    enabled = parse is ParseResult.Ok,
                    onClick = {
                        val key = VlessKey(editingId ?: UUID.randomUUID().toString(), keyName(field), field.trim())
                        scope.launch {
                            if (editingId == null) store.addVlessKey(key) else store.updateVlessKey(key)
                            VpnController.restartIfActive(ctx)
                            editing = false
                        }
                    },
                    height = 48,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Spacing.space24))
      }
      if (!editing) {
          FloatingActionButton(
              onClick = { beginEdit(null) },
              modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
              containerColor = c.accent,
              contentColor = c.onAccent,
          ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
      }
    }
}

@Composable
private fun KeyRow(key: VlessKey, selected: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onClick: () -> Unit) {
    val c = detourColors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = Spacing.space16, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(keyName(key.uri), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(serverValue(key), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.width(80.dp), horizontalArrangement = Arrangement.End) {
            IconButton(modifier = Modifier.size(40.dp), onClick = onEdit) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.key_edit), tint = c.textSecondary)
            }
            IconButton(modifier = Modifier.size(40.dp), onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.key_delete), tint = c.error)
            }
        }
    }
}

private fun serverValue(key: VlessKey): String =
    (VlessKeyParser.parse(key.uri) as? ParseResult.Ok)?.profile?.let { "${it.server}:${it.port}" } ?: "—"

private fun keyName(uri: String): String =
    (VlessKeyParser.parse(uri) as? ParseResult.Ok)?.profile?.let { it.name.ifBlank { it.server } } ?: "VLESS"

private fun profileValue(key: VlessKey, value: (dev.triplet.app.core.VlessProfile) -> String): String =
    (VlessKeyParser.parse(key.uri) as? ParseResult.Ok)?.profile?.let { value(it).ifBlank { "—" } } ?: "—"

@Composable
private fun VlessMask(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(14) { Box(Modifier.size(5.dp).background(detourColors.textMuted, androidx.compose.foundation.shape.CircleShape)) }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val c = detourColors
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.space16, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
