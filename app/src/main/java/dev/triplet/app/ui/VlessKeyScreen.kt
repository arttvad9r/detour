package dev.triplet.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.window.Dialog
import dev.triplet.app.R
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.core.WarpConfigImporter
import dev.triplet.app.core.WarpImportResult
import dev.triplet.app.core.WarpProfile
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun VlessKeyScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val settings by store.settings.collectAsState()
    val keys = settings?.vlessKeys
    val vlessItems = keys?.items.orEmpty()
    val warpProfile = settings?.warpProfile
    val activeVpn = settings?.activeVpn ?: VpnProfileKind.VLESS
    val addDescription = stringResource(R.string.profile_add_title)
    val vlessTitle = stringResource(R.string.profile_add_vless)

    var editingId by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var editing by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var showAddDialog by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var field by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var warpStatus by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val parse = field.trim().takeIf { it.isNotBlank() }?.let(VlessKeyParser::parse)

    fun beginEdit(key: VlessKey?) {
        editing = true
        editingId = key?.id
        field = key?.uri ?: ""
    }

    val warpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val raw = readWarpConfig(ctx, uri) ?: return@withContext WarpImportResult.Invalid
                    WarpConfigImporter.parse(raw)
                }
            }.getOrElse { WarpImportResult.Invalid }
            when (result) {
                is WarpImportResult.Ok -> {
                    store.setWarpProfile(result.profile)
                    VpnController.restartIfActive(ctx)
                    // The new row is sufficient success feedback; keep the area
                    // below the list reserved for actionable import errors only.
                    warpStatus = 0
                }
                WarpImportResult.NoCompatibleProxies -> warpStatus = 2
                WarpImportResult.Invalid -> warpStatus = 3
            }
        }
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

                if (vlessItems.isEmpty() && warpProfile == null) {
                    Text(
                        stringResource(R.string.profile_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textMuted,
                        modifier = Modifier.padding(
                            start = Spacing.space20,
                            end = Spacing.space20,
                            top = Spacing.space4,
                            bottom = Spacing.space12,
                        ),
                    )
                } else {
                    DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                        vlessItems.forEachIndexed { index, key ->
                            KeyRow(
                                key = key,
                                selected = activeVpn == VpnProfileKind.VLESS && key.id == keys?.activeId,
                                onEdit = { beginEdit(key) },
                                onDelete = {
                                    scope.launch {
                                        store.deleteVlessKey(key.id)
                                        VpnController.restartIfActive(ctx)
                                    }
                                },
                            ) {
                                scope.launch {
                                    store.setActiveVlessKey(key.id)
                                    VpnController.restartIfActive(ctx)
                                }
                            }
                            if (index < vlessItems.lastIndex || warpProfile != null) {
                                GroupDivider(startInset = 16)
                            }
                        }
                        warpProfile?.let { profile ->
                            WarpRow(
                                profile = profile,
                                selected = activeVpn == VpnProfileKind.WARP,
                                onDelete = {
                                    scope.launch {
                                        store.deleteWarpProfile()
                                        VpnController.restartIfActive(ctx)
                                    }
                                },
                                onClick = {
                                    scope.launch {
                                        store.setActiveVpn(VpnProfileKind.WARP)
                                        VpnController.restartIfActive(ctx)
                                    }
                                },
                            )
                        }
                    }
                }

                if (warpStatus != 0) {
                    Spacer(Modifier.height(Spacing.space8))
                    Text(
                        text = stringResource(
                            if (warpStatus == 2) R.string.warp_invalid else R.string.warp_import_error,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.error,
                        modifier = Modifier.padding(horizontal = Spacing.space20),
                    )
                }
                Spacer(Modifier.height(Spacing.space8))
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.space16)) {
                    DetourInputField(
                        value = field,
                        onValueChange = { value ->
                            field = value.replace("\r", "").replace("\n", "")
                        },
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
                        maxHeight = 176.dp,
                        maxLines = 6,
                    )

                    val clipboard = LocalClipboard.current
                    TextButton(
                        onClick = {
                            scope.launch {
                                clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()?.let {
                                    field = it.trim().replace("\r", "").replace("\n", "")
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
                                    keyName(value, vlessTitle),
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
                onClick = { showAddDialog = true },
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

    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            DetourCard {
                Text(
                    stringResource(R.string.profile_add_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    modifier = Modifier.padding(
                        start = Spacing.space20,
                        end = Spacing.space20,
                        top = Spacing.space16,
                        bottom = Spacing.space12,
                    ),
                )
                GroupDivider(startInset = 20)
                ProfileTypeRow(
                    title = stringResource(R.string.profile_add_vless),
                    subtitle = stringResource(R.string.profile_add_vless_sub),
                ) {
                    showAddDialog = false
                    beginEdit(null)
                }
                GroupDivider(startInset = 20)
                ProfileTypeRow(
                    title = stringResource(R.string.profile_add_warp),
                    subtitle = stringResource(
                        if (warpProfile == null) R.string.profile_add_warp_sub
                        else R.string.profile_replace_warp_sub,
                    ),
                ) {
                    showAddDialog = false
                    warpStatus = 0
                    warpLauncher.launch(arrayOf("*/*"))
                }
                Row(
                    Modifier.fillMaxWidth().padding(
                        start = Spacing.space12,
                        end = Spacing.space12,
                        bottom = Spacing.space4,
                    ),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text(stringResource(R.string.key_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTypeRow(title: String, subtitle: String, onClick: () -> Unit) {
    val c = detourColors
    Row(
        Modifier.fillMaxWidth()
            .detourClickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = Spacing.space20, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Chevron()
    }
}

@Composable
private fun KeyRow(
    key: VlessKey,
    selected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
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
                keyName(key.uri, "VLESS"),
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

@Composable
private fun WarpRow(profile: WarpProfile, selected: Boolean, onDelete: () -> Unit, onClick: () -> Unit) {
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
                profile.name,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.warp_subtitle, profile.proxies.size),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(modifier = Modifier.size(40.dp), onClick = onDelete) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.warp_delete),
                tint = c.error,
            )
        }
    }
}

private fun readWarpConfig(context: android.content.Context, uri: Uri): String? {
    val input = context.contentResolver.openInputStream(uri) ?: return null
    input.use {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            total += count
            if (total > WarpConfigImporter.MAX_CHARS) return null
            out.write(buffer, 0, count)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

private fun serverValue(key: VlessKey): String =
    (VlessKeyParser.parse(key.uri) as? ParseResult.Ok)?.profile?.let { "${it.server}:${it.port}" } ?: "—"

private fun keyName(uri: String, fallback: String): String =
    (VlessKeyParser.parse(uri) as? ParseResult.Ok)?.profile?.let { it.name.ifBlank { it.server } } ?: fallback
