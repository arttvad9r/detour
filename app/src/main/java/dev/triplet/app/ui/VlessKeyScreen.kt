package dev.triplet.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.core.WarpConfigImporter
import dev.triplet.app.core.WarpProfile
import kotlinx.coroutines.CancellationException
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
fun VlessKeyScreen(viewModel: ProfilesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val c = detourColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vlessItems = state.vlessItems
    val warpProfile = state.warpProfile
    val activeVpn = state.activeVpn
    val activeVlessId = state.activeVlessId
    val warpImportStatus = state.warpImportStatus
    val warpImporting = warpImportStatus == WarpImportStatus.IMPORTING
    val vlessSaveStatus = state.vlessSaveStatus
    val vlessSaving = vlessSaveStatus == VlessSaveStatus.SAVING
    val addDescription = stringResource(R.string.profile_add_title)
    val vlessTitle = stringResource(R.string.profile_add_vless)

    var editingId by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var showSheet by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var sheetMode by rememberSaveable { androidx.compose.runtime.mutableStateOf(ProfileSheetMode.PICKER) }
    var field by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val parse = remember(field) {
        field.trim().takeIf { it.isNotBlank() }?.let(VlessKeyParser::parse)
    }
    val currentVlessSaving = rememberUpdatedState(vlessSaving)
    val confirmSheetValueChange = remember {
        { target: SheetValue -> !currentVlessSaving.value || target != SheetValue.Hidden }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmSheetValueChange,
    )

    LaunchedEffect(vlessSaveStatus, sheetState) {
        if (vlessSaveStatus == VlessSaveStatus.SAVED) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            runCatching { sheetState.hide() }
            showSheet = false
            viewModel.acknowledgeVlessSave()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.warpSaved.collect {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.warpImportRejected.collect {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
        }
    }

    fun beginVlessEdit(key: VlessKey?) {
        viewModel.clearVlessSaveError()
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
        if (uri == null || !viewModel.beginWarpImport()) return@rememberLauncherForActivityResult
        scope.launch {
            var handedOff = false
            try {
                val raw = try {
                    withContext(Dispatchers.IO) {
                        readWarpConfig(ctx, uri)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    viewModel.reportWarpImportReadError()
                    return@launch
                }
                viewModel.importWarp(raw)
                handedOff = true
            } finally {
                if (!handedOff) viewModel.cancelWarpImport()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = c.background,
        floatingActionButton = {
            ProfileAddFab(
                visible = !showSheet && !warpImporting,
                addDescription = addDescription,
                onClick = {
                    viewModel.clearVlessSaveError()
                    editingId = null
                    field = ""
                    sheetMode = ProfileSheetMode.PICKER
                    showSheet = true
                },
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(stringResource(R.string.key_title), onBack)

            DetourContentColumn {
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
                            tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING),
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
                                .selectableGroup(),
                        ) {
                            shown.vless.forEachIndexed { index, key ->
                                val selected = activeVpn == VpnProfileKind.VLESS && key.id == activeVlessId
                                KeyRow(
                                    key = key,
                                    selected = selected,
                                    onEdit = { beginVlessEdit(key) },
                                    onDelete = { viewModel.deleteVless(key.id) },
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    viewModel.selectVless(key.id)
                                }
                                if (index < shown.vless.lastIndex || shown.warp != null) {
                                    GroupDivider(startInset = 52)
                                }
                            }
                            shown.warp?.let { profile ->
                                val selected = activeVpn == VpnProfileKind.WARP
                                WarpRow(
                                    profile = profile,
                                    selected = selected,
                                    importing = warpImporting,
                                    onEdit = { warpLauncher.launch(arrayOf("*/*")) },
                                    onDelete = viewModel::deleteWarp,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        viewModel.selectWarp()
                                    },
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = warpImportStatus != WarpImportStatus.IDLE,
                    enter = fadeIn(tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING)),
                    exit = fadeOut(tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING)),
                ) {
                    Column {
                        Spacer(Modifier.height(Spacing.space8))
                        when (warpImportStatus) {
                            WarpImportStatus.IMPORTING -> {
                                Row(
                                    modifier = Modifier.padding(horizontal = Spacing.space20),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = c.accent,
                                    )
                                    Spacer(Modifier.size(Spacing.space8))
                                    Text(
                                        text = stringResource(R.string.warp_importing),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textSecondary,
                                    )
                                }
                            }
                            WarpImportStatus.NO_COMPATIBLE_PROXIES,
                            WarpImportStatus.ERROR -> {
                                Text(
                                    text = stringResource(
                                        if (warpImportStatus == WarpImportStatus.NO_COMPATIBLE_PROXIES) {
                                            R.string.warp_invalid
                                        } else {
                                            R.string.warp_import_error
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.error,
                                    modifier = Modifier.padding(horizontal = Spacing.space20),
                                )
                            }
                            WarpImportStatus.IDLE -> Unit
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.space24))
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { if (!vlessSaving) showSheet = false },
            sheetState = sheetState,
            containerColor = c.surface,
            contentColor = c.textPrimary,
        ) {
            AnimatedContent(
                targetState = sheetMode,
                transitionSpec = {
                    if (targetState == ProfileSheetMode.VLESS_EDITOR) {
                        (fadeIn(
                            tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING),
                        ) + slideInHorizontally(
                            tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING),
                            initialOffsetX = { it / 24 },
                        )) togetherWith
                            (fadeOut(
                                tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
                            ) + slideOutHorizontally(
                                tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
                                targetOffsetX = { -it / 48 },
                            ))
                    } else {
                        (fadeIn(
                            tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING),
                        ) + slideInHorizontally(
                            tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING),
                            initialOffsetX = { -it / 24 },
                        )) togetherWith
                            (fadeOut(
                                tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
                            ) + slideOutHorizontally(
                                tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
                                targetOffsetX = { it / 48 },
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
                                    viewModel.clearVlessSaveError()
                                    field = value.replace("\r", "").replace("\n", "")
                                },
                                label = stringResource(R.string.key_uri),
                                placeholder = stringResource(R.string.key_placeholder),
                                helper = stringResource(R.string.key_input_hint),
                                error = when {
                                    parse is ParseResult.Err -> stringResource(R.string.key_invalid)
                                    vlessSaveStatus == VlessSaveStatus.ERROR -> stringResource(R.string.vless_save_error)
                                    else -> null
                                },
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
                                            viewModel.clearVlessSaveError()
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
                                    enabled = !vlessSaving,
                                    style = ButtonStyle.SECONDARY,
                                    modifier = Modifier.weight(1f),
                                )
                                DetourButton(
                                    text = stringResource(
                                        if (vlessSaving) R.string.vless_saving else R.string.btn_save,
                                    ),
                                    enabled = parse is ParseResult.Ok && !vlessSaving,
                                    onClick = {
                                        val value = field.trim()
                                        val parsedProfile = (parse as? ParseResult.Ok)?.profile
                                        val key = VlessKey(
                                            editingId ?: UUID.randomUUID().toString(),
                                            parsedProfile?.name?.ifBlank { parsedProfile.server } ?: vlessTitle,
                                            value,
                                        )
                                        viewModel.saveVless(key, isNew = editingId == null)
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
private fun ProfileAddFab(
    visible: Boolean,
    addDescription: String,
    onClick: () -> Unit,
) {
    val c = detourColors
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.navigationBarsPadding(),
        enter = fadeIn(tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING)) + scaleIn(
            animationSpec = spring(
                dampingRatio = Motion.SPRING_DAMPING,
                stiffness = Motion.SPRING_STIFFNESS_SOFT,
            ),
            initialScale = 0.96f,
        ),
        exit = fadeOut(tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING)) + scaleOut(
            animationSpec = tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
            targetScale = 0.96f,
        ),
    ) {
        val fabInteraction = remember { MutableInteractionSource() }
        val fabPressed by fabInteraction.collectIsPressedAsState()
        val fabScale by animateFloatAsState(
            targetValue = if (fabPressed) Motion.PRESS_FAB else 1f,
            animationSpec = spring(
                dampingRatio = Motion.SPRING_DAMPING,
                stiffness = Motion.SPRING_STIFFNESS,
            ),
            label = "profileFabPress",
        )
        FloatingActionButton(
            onClick = onClick,
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
    val profile = remember(key.uri) {
        (VlessKeyParser.parse(key.uri) as? ParseResult.Ok)?.profile
    }
    Row(
        Modifier.fillMaxWidth()
            .detourSelectable(
                selected = selected,
                onClick = { if (!selected) onClick() },
                idleColor = if (selected) c.accentSoft else Color.Transparent,
                pressedColor = if (selected) c.accentSoft else c.surfaceSelected,
                pressScale = Motion.PRESS_RADIO,
            )
            .padding(start = Spacing.space16, top = 10.dp, bottom = 10.dp, end = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        Column(Modifier.padding(start = Spacing.space12).weight(1f)) {
            Text(
                profile?.name?.ifBlank { profile.server } ?: "VLESS",
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                profile?.let { "${it.server}:${it.port}" } ?: "—",
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
    importing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val c = detourColors
    Row(
        Modifier.fillMaxWidth()
            .detourSelectable(
                selected = selected,
                onClick = { if (!selected) onClick() },
                idleColor = if (selected) c.accentSoft else Color.Transparent,
                pressedColor = if (selected) c.accentSoft else c.surfaceSelected,
                pressScale = Motion.PRESS_RADIO,
            )
            .padding(start = Spacing.space16, top = 10.dp, bottom = 10.dp, end = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
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
        if (importing) {
            CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = Spacing.space12).size(18.dp),
                strokeWidth = 2.dp,
                color = c.accent,
            )
        } else {
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
