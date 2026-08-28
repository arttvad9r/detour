package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.triplet.app.R
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun VlessKeyScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState()
    val keys = settings?.vlessKeys
    val addDescription = stringResource(R.string.key_add)
    val keyTitle = stringResource(R.string.key_title)

    var editingId by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var editing by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var field by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val parse = field.trim().takeIf { it.isNotBlank() }?.let(VlessKeyParser::parse)

    fun beginEdit(key: VlessKey?) {
        editing = true
        editingId = key?.id
        field = key?.uri ?: ""
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
                    modifier = Modifier.padding(horizontal = Spacing.space16),
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
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.space16)) {
                    DetourInputField(
                        value = field,
                        onValueChange = { field = it },
                        label = stringResource(R.string.key_uri),
                        placeholder = stringResource(R.string.key_placeholder),
                        helper = stringResource(R.string.key_input_hint),
                        error = if (parse is ParseResult.Err) stringResource(R.string.key_invalid) else null,
                        success = (parse as? ParseResult.Ok)?.let { result ->
                            stringResource(
                                R.string.key_detected_server,
                                result.profile.server,
                                result.profile.port,
                            )
                        },
                        singleLine = false,
                        minHeight = 56.dp,
                        maxHeight = 120.dp,
                        maxLines = 5,
                        monospace = true,
                    )

                    val clipboard = LocalClipboard.current
                    TextButton(
                        onClick = {
                            scope.launch {
                                clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()?.let {
                                    field = it.trim()
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.key_paste))
                    }

                    Spacer(Modifier.height(Spacing.space4))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                    ) {
                        DetourButton(
                            text = stringResource(R.string.key_cancel),
                            onClick = { editing = false },
                            style = ButtonStyle.SECONDARY,
                            modifier = Modifier.weight(1f),
                        )
                        DetourButton(
                            text = stringResource(R.string.btn_save),
                            enabled = parse is ParseResult.Ok,
                            onClick = {
                                val value = field.trim()
                                val key = VlessKey(
                                    editingId ?: UUID.randomUUID().toString(),
                                    keyName(value, keyTitle),
                                    value,
                                )
                                scope.launch {
                                    if (editingId == null) store.addVlessKey(key) else store.updateVlessKey(key)
                                    VpnController.restartIfActive(ctx)
                                    editing = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.space24))
        }
        if (!editing) {
            FloatingActionButton(
                onClick = { beginEdit(null) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)
                    .semantics {
                        contentDescription = addDescription
                        role = Role.Button
                    },
                shape = AppShapes.small,
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
        Modifier.fillMaxWidth()
            .detourClickable(
                onClick = onClick,
                role = Role.RadioButton,
                idleColor = if (selected) c.accentSoft else Color.Transparent,
                pressedColor = if (selected) c.accentSoft else c.surfaceSelected,
            )
            .padding(start = Spacing.space16, top = Spacing.space12, bottom = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected)
        Column(Modifier.padding(start = Spacing.space12).weight(1f)) {
            Text(
                keyName(key.uri, stringResource(R.string.key_title)),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                serverValue(key),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.width(80.dp), horizontalArrangement = Arrangement.End) {
            IconButton(modifier = Modifier.size(40.dp), onClick = onEdit) {
                Icon(
                    painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.key_edit),
                    tint = c.textSecondary,
                )
            }
            IconButton(modifier = Modifier.size(40.dp), onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.key_delete),
                    tint = c.error,
                )
            }
        }
    }
}

private fun serverValue(key: VlessKey): String =
    (VlessKeyParser.parse(key.uri) as? ParseResult.Ok)?.profile?.let { "${it.server}:${it.port}" } ?: "—"

private fun keyName(uri: String, fallback: String): String =
    (VlessKeyParser.parse(uri) as? ParseResult.Ok)?.profile?.let { it.name.ifBlank { it.server } } ?: fallback