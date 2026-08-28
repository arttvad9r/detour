package dev.triplet.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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

private data class ProfileSnapshot(
    val vless: List<VlessKey>,
    val warp: WarpProfile?,
)

private enum class ProfileSheetMode { PICKER, VLESS_EDITOR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlessKeyScreen(store: RoutesStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
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
    var showSheet by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var sheetMode by remember { androidx.compose.runtime.mutableStateOf(ProfileSheetMode.PICKER) }
    var field by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var warpStatus by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val parse = field.trim().takeIf { it.isNotBlank() }?.let(VlessKeyParser::parse)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun beginVlessEdit(key: VlessKey?) {
        editingId = key?.id
        field = key?.uri ?: ""
        sheetMode = ProfileSheetMode.VLESS_EDITOR
        showSheet = true
    }

    fun dismissSheet(after: (() -> Unit)? = null) {
        scope.launch {
            runCatching { sheetState.hide() }
            showSheet = false
            after?.invoke()
        }
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
                    warpStatus = 0
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                WarpImportResult.NoCompatibleProxies -> {
                    warpStatus = 2
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }
                WarpImportResult.Invalid -> {
                    warpStatus = 3
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }
            }
        }
    }

    Box(modifier.fillMaxSize().background(c.background)) {
        Column(
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(stringResource(R.string.key_title), onBack)
            Spacer(Modifier.height(Spacing.space8))

            Text(
                stringResource(R.string.key_list_title),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )
            Spacer(Modifier.height(Spacing.space8))

            val snapshot = ProfileSnapshot(vlessItems, warpProfile)
            AnimatedContent(
                targetState = snapshot,
                transitionSpec = {
                    fadeIn(
                        tween(
                            Motion.CONTENT_IN_MS,
                            delayMillis = 20,
                            easing = Motion.ENTER_EASING,
                        ),
                    ) togetherWith fadeOut(
                        tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
                    )
                },
                label = "profileList",
            ) { shown ->
                if (shown.vless.isEmpty() && shown.warp == null) {
                    Text(
                        stringResource(R.string.profile_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                        modifier = Modifier.padding(
                            start = Spacing.space20,
                            end = Spacing.space20,
                            top = Spacing.space2,
                            bottom = Spacing.space12,
                        ),
                    )
                } else {
                    DetourCard(
                        Modifier
                            .padding(horizontal = Spacing.space16)
                            .animateContentSize(
                                spring(
                                    dampingRatio = Motion.SPRING_DAMPING,
                                    stiffness = Motion.SPRING_STIFFNESS_SOFT,
                                ),
                            ),
                    ) {
                        shown.vless.forEachIndexed { index, key ->
                            val selected = activeVpn == VpnProfileKind.VLESS && key.id == keys?.activeId
                            KeyRow(
                                key = key,
                                selected = selected,
                                onEdit = { beginVlessEdit(key) },
                                onDelete = {
                                    scope.launch {
                                        store.deleteVlessKey(key.id)
                                        VpnController.restartIfActive(ctx)
                                    }
                                },
                            ) {
                                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                scope.launch {
                                    store.setActiveVlessKey(key.id)
                                    VpnController.restartIfActive(ctx)
                                }
                            }
                            if (index < shown.vless.lastIndex || shown.warp != null) {
                                GroupDivider(startInset = 16)
                            }
                        }
                        shown.warp?.let { profile ->
                            val selected = activeVpn == VpnProfileKind.WARP
                            WarpRow(
                                profile = profile,
                                selected = selected,
                                onEdit = {
                                    warpStatus = 0
                                    warpLauncher.launch(arrayOf("*/*"))
                                },
                                onDelete = {
                                    scope.launch {
                                        store.deleteWarpProfile()
                                        VpnController.restartIfActive(ctx)
                                    }
                                },
                                onClick = {
                                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    scope.launch {
                                        store.setActiveVpn(VpnProfileKind.WARP)
                                        VpnController.restartIfActive(ctx)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = warpStatus != 0,
                enter = fadeIn(tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING)),
                exit = fadeOut(tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING)),
            ) {
                Column {
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
            }
            Spacer(Modifier.height(Spacing.space24))
        }

        AnimatedVisibility(
            visible = !showSheet,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
            enter = fadeIn(tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING)) + scaleIn(
                animationSpec = spring(
                    dampingRatio = Motion.SPRING_DAMPING,
                    stiffness = Motion.SPRING_STIFFNESS_SOFT,
                ),
                initialScale = 0.90f,
            ),
            exit = fadeOut(tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING)) + scaleOut(
                animationSpec = tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
                targetScale = 0.90f,
            ),
        ) {
            val fabInteraction = remember { MutableInteractionSource() }
            val fabPressed by fabInteraction.collectIsPressedAsState()
            val fabScale by animateFloatAsState(
                targetValue = if (fabPressed) Motion.PRESS_FAB else 1f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Motion.SPRING_STIFFNESS,
                ),
                label = "profileFabPress",
            )
            FloatingActionButton(
                onClick = {
                    editingId = null
                    field = ""
                    sheetMode = ProfileSheetMode.PICKER
                    showSheet = true
                },
                interactionSource = fabInteraction,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    }
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

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = c.surface,
            contentColor = c.textPrimary,
        ) {
            AnimatedContent(
                targetState = sheetMode,
                transitionSpec = {
                    if (targetState == ProfileSheetMode.VLESS_EDITOR) {
                        (fadeIn(
                            tween(
                                Motion.CONTENT_IN_MS,
                                delayMillis = 20,
                                easing = Motion.ENTER_EASING,
                            ),
                        ) + slideInHorizontally(
                            tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING),
                            initialOffsetX = { it / 12 },
                        )) togetherWith
                            (fadeOut(
                                tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
                            ) + slideOutHorizontally(
                                tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
                                targetOffsetX = { -it / 36 },
                            ))
                    } else {
                        (fadeIn(
                            tween(
                                Motion.CONTENT_IN_MS,
                                delayMillis = 14,
                                easing = Motion.ENTER_EASING,
                            ),
                        ) + slideInHorizontally(
                            tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING),
                            initialOffsetX = { -it / 36 },
                        )) togetherWith
                            (fadeOut(
                                tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
                            ) + slideOutHorizontally(
                                tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
                                targetOffsetX = { it / 12 },
                            ))
                    }
                },
                label = "profileSheetContent",
            ) { mode ->
                when (mode) {
                    ProfileSheetMode.PICKER -> {
                        Column {
                            Text(
                                stringResource(R.string.profile_add_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = c.textPrimary,
                                modifier = Modifier.padding(
                                    start = Spacing.space20,
                                    end = Spacing.space20,
                                    top = Spacing.space8,
                                    bottom = Spacing.space12,
                                ),
                            )
                            GroupDivider(startInset = 20)
                            ProfileTypeRow(
                                title = stringResource(R.string.profile_add_vless),
                                subtitle = stringResource(R.string.profile_add_vless_sub),
                            ) {
                                editingId = null
                                field = ""
                                sheetMode = ProfileSheetMode.VLESS_EDITOR
                            }
                            GroupDivider(startInset = 20)
                            ProfileTypeRow(
                                title = stringResource(R.string.profile_add_warp),
                                subtitle = stringResource(
                                    if (warpProfile == null) R.string.profile_add_warp_sub
                                    else R.string.profile_replace_warp_sub,
                                ),
                            ) {
                                dismissSheet {
                                    warpStatus = 0
                                    warpLauncher.launch(arrayOf("*/*"))
                                }
                            }
                            Spacer(Modifier.height(Spacing.space8))
                            TextButton(
                                onClick = { dismissSheet() },
                                modifier = Modifier.align(Alignment.End).padding(end = Spacing.space12),
                            ) {
                                Text(stringResource(R.string.key_cancel))
                            }
                            Spacer(Modifier.navigationBarsPadding().height(Spacing.space8))
                        }
                    }

                    ProfileSheetMode.VLESS_EDITOR -> {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .imePadding()
                                .padding(horizontal = Spacing.space20),
                        ) {
                            Text(
                                stringResource(
                                    if (editingId == null) R.string.vless_add_title else R.string.vless_edit_title,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = c.textPrimary,
                                modifier = Modifier.padding(top = Spacing.space8, bottom = Spacing.space16),
                            )
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
                                maxHeight = 144.dp,
                                maxLines = 5,
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

                            Spacer(Modifier.height(Spacing.space8))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                            ) {
                                DetourButton(
                                    text = stringResource(R.string.key_cancel),
                                    onClick = {
                                        if (editingId == null) {
                                            field = ""
                                            sheetMode = ProfileSheetMode.PICKER
                                        } else {
                                            dismissSheet()
                                        }
                                    },
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
                                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                            runCatching { sheetState.hide() }
                                            showSheet = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.navigationBarsPadding().height(Spacing.space16))
                        }
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
            .detourClickable(
                onClick = onClick,
                role = Role.Button,
                pressedColor = c.surfaceSelected.copy(alpha = 0.42f),
                pressScale = Motion.PRESS_ROW,
            )
            .padding(horizontal = Spacing.space20, vertical = 12.dp),
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
                idleColor = if (selected) c.surfaceSelected else Color.Transparent,
                pressedColor = if (selected) c.surfaceSelected else c.accentSoft,
                pressScale = Motion.PRESS_RADIO,
            )
            .padding(start = Spacing.space16, top = 10.dp, bottom = 10.dp, end = Spacing.space8),
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
        Row(horizontalArrangement = Arrangement.End) {
            DetourIconButton(onClick = onEdit, size = 36) {
                Icon(
                    painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.key_edit),
                    tint = c.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            DetourIconButton(onClick = onDelete, size = 36) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.key_delete),
                    tint = c.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun WarpRow(
    profile: WarpProfile,
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
                idleColor = if (selected) c.surfaceSelected else Color.Transparent,
                pressedColor = if (selected) c.surfaceSelected else c.accentSoft,
                pressScale = Motion.PRESS_RADIO,
            )
            .padding(start = Spacing.space16, top = 10.dp, bottom = 10.dp, end = Spacing.space8),
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
        Row(horizontalArrangement = Arrangement.End) {
            DetourIconButton(onClick = onEdit, size = 36) {
                Icon(
                    painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.warp_replace),
                    tint = c.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            DetourIconButton(onClick = onDelete, size = 36) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.warp_delete),
                    tint = c.error,
                    modifier = Modifier.size(18.dp),
                )
            }
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
