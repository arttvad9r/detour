package dev.detour.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.detour.app.R
import dev.detour.app.core.ParseResult
import dev.detour.app.core.VlessKey
import dev.detour.app.core.VlessKeyParser
import dev.detour.app.core.VlessProfile
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.core.WarpProfile
import kotlinx.coroutines.launch
import java.util.UUID

private const val PROFILES_SCREEN_TEST_TAG = "profiles_screen"

private data class ProfileGroups(
    val vless: List<VlessKey>,
    val subscriptions: List<VlessKey>,
    val warp: WarpProfile?,
)

private fun parsedProfile(key: VlessKey): VlessProfile? =
    (VlessKeyParser.parse(key.uri) as? ParseResult.Ok)?.profile

private fun profileTabFor(kind: VpnProfileKind): Int = when (kind) {
    VpnProfileKind.VLESS -> 0
    VpnProfileKind.SUBSCRIPTION -> 1
    VpnProfileKind.WARP -> 2
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlessKeyScreen(viewModel: ProfilesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
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
    val vlessFallbackTitle = stringResource(R.string.protocol_vless)
    val subscriptionFallbackTitle = stringResource(R.string.subscription_profile_section)

    val groups = remember(vlessItems, warpProfile) {
        ProfileGroups(
            vless = vlessItems.filter { parsedProfile(it)?.isSubscription != true },
            subscriptions = vlessItems.filter { parsedProfile(it)?.isSubscription == true },
            warp = warpProfile,
        )
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(profileTabFor(activeVpn)) }
    LaunchedEffect(activeVpn) {
        selectedTab = profileTabFor(activeVpn)
    }

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorTab by rememberSaveable { mutableIntStateOf(0) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    // Credential drafts deliberately stay process-memory-only. Recreating the
    // Activity must not serialize a VLESS/subscription URI into saved instance state.
    var field by remember { mutableStateOf("") }
    val parse = remember(field) {
        field.trim().takeIf { it.isNotBlank() }?.let(VlessKeyParser::parse)
    }
    val parsed = parse as? ParseResult.Ok
    val expectsSubscription = editorTab == 1
    val parsedMatchesEditor = parsed?.profile?.isSubscription == expectsSubscription
    val currentVlessSaving = rememberUpdatedState(vlessSaving)
    val confirmSheetValueChange = remember {
        { target: SheetValue -> !currentVlessSaving.value || target != SheetValue.Hidden }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmSheetValueChange,
    )

    val activity = LocalContext.current.findActivity()
    DisposableEffect(showEditor, activity) {
        if (showEditor) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (showEditor) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    val warpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importWarpDocument(it.toString()) }
    }

    LaunchedEffect(vlessSaveStatus, sheetState) {
        if (vlessSaveStatus == VlessSaveStatus.SAVED) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            runCatching { sheetState.hide() }
            showEditor = false
            field = ""
            viewModel.acknowledgeVlessSave()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.warpSaved.collect { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    }
    LaunchedEffect(viewModel) {
        viewModel.warpImportRejected.collect { haptics.performHapticFeedback(HapticFeedbackType.Reject) }
    }

    fun beginEditor(key: VlessKey? = null, tab: Int = selectedTab) {
        viewModel.clearVlessSaveError()
        editingId = key?.id
        field = key?.uri ?: ""
        editorTab = key?.let { if (parsedProfile(it)?.isSubscription == true) 1 else 0 }
            ?: tab.coerceIn(0, 1)
        showEditor = true
    }

    fun dismissEditor() {
        scope.launch {
            runCatching { sheetState.hide() }
            showEditor = false
            field = ""
        }
    }

    val addButtonText = when (selectedTab) {
        0 -> stringResource(R.string.profile_add_vless_action)
        1 -> stringResource(R.string.profile_add_subscription_action)
        else -> stringResource(if (warpProfile == null) R.string.warp_import else R.string.warp_replace)
    }

    Scaffold(
        modifier = modifier
            .testTag(PROFILES_SCREEN_TEST_TAG)
            .fillMaxSize(),
        containerColor = c.background,
        bottomBar = {
            if (!showEditor && !warpImporting) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
                ) {
                    DetourButton(
                        text = addButtonText,
                        onClick = {
                            if (selectedTab == 2) {
                                warpLauncher.launch(arrayOf("*/*"))
                            } else {
                                beginEditor(tab = selectedTab)
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .detourHighRefresh(scrollState.isScrollInProgress),
        ) {
            DetourBrandedHeader(stringResource(R.string.key_title), onBack)

            DetourContentColumn {
                Spacer(Modifier.height(Spacing.space8))
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.protocol_vless),
                        stringResource(R.string.subscription_profile_section),
                        stringResource(R.string.protocol_warp),
                    ),
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
                Spacer(Modifier.height(Spacing.space16))

                when (selectedTab) {
                    0 -> ProfileKeyList(
                        items = groups.vless,
                        kind = VpnProfileKind.VLESS,
                        activeVpn = activeVpn,
                        activeVlessId = activeVlessId,
                        onEdit = { beginEditor(it) },
                        onDelete = viewModel::deleteVless,
                        onSelect = { keyId ->
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            viewModel.selectVless(keyId)
                        },
                    )
                    1 -> {
                        ProfileKeyList(
                            items = groups.subscriptions,
                            kind = VpnProfileKind.SUBSCRIPTION,
                            activeVpn = activeVpn,
                            activeVlessId = activeVlessId,
                            onEdit = { beginEditor(it) },
                            onDelete = viewModel::deleteVless,
                            onSelect = { keyId ->
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                viewModel.selectVless(keyId)
                            },
                        )
                        val activeSubscription = groups.subscriptions.firstOrNull {
                            it.id == activeVlessId && activeVpn == VpnProfileKind.SUBSCRIPTION
                        }
                        if (activeSubscription != null) {
                            Spacer(Modifier.height(Spacing.space16))
                            SubscriptionRuntimeSection()
                        }
                    }
                    2 -> WarpProfileList(
                        profile = groups.warp,
                        selected = activeVpn == VpnProfileKind.WARP,
                        importing = warpImporting,
                        onEdit = { warpLauncher.launch(arrayOf("*/*")) },
                        onDelete = viewModel::deleteWarp,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            viewModel.selectWarp()
                        },
                    )
                }

                if (warpImportStatus != WarpImportStatus.IDLE) {
                    Spacer(Modifier.height(Spacing.space12))
                    ProfileOperationNotice(warpImportStatus)
                }
                Spacer(Modifier.height(Spacing.space24))
            }
        }
    }

    if (showEditor) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!vlessSaving) {
                    showEditor = false
                    field = ""
                }
            },
            sheetState = sheetState,
            containerColor = c.background,
            contentColor = c.textPrimary,
        ) {
             Column(
                 Modifier
                     .fillMaxWidth()
                     .imePadding()
                     .verticalScroll(rememberScrollState())
                     .padding(horizontal = Spacing.space20),
            ) {
                Row(
                    Modifier.padding(top = Spacing.space4, bottom = Spacing.space16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetourIconTile(
                        iconRes = if (expectsSubscription) R.drawable.ic_globe else R.drawable.ic_lock,
                        selected = true,
                    )
                    Text(
                        text = when {
                            editingId != null -> stringResource(R.string.vless_edit_title)
                            expectsSubscription -> stringResource(R.string.profile_add_subscription_action)
                            else -> stringResource(R.string.profile_add_vless_action)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        modifier = Modifier.padding(start = Spacing.space12),
                    )
                }

                val contextError = when {
                    parse is ParseResult.Err -> stringResource(
                        if (expectsSubscription) R.string.profile_subscription_invalid
                        else R.string.profile_vless_invalid,
                    )
                    parsed != null && !parsedMatchesEditor -> stringResource(
                        if (expectsSubscription) R.string.profile_subscription_wrong_type
                        else R.string.profile_vless_wrong_type,
                    )
                    vlessSaveStatus == VlessSaveStatus.ERROR -> stringResource(R.string.vless_save_error)
                    else -> null
                }

                DetourInputField(
                    value = field,
                    onValueChange = { value ->
                        viewModel.clearVlessSaveError()
                        field = value.replace("\r", "").replace("\n", "")
                    },
                    label = stringResource(
                        if (expectsSubscription) R.string.profile_subscription_input_label
                        else R.string.profile_vless_input_label,
                    ),
                    placeholder = stringResource(
                        if (expectsSubscription) R.string.profile_subscription_placeholder
                        else R.string.profile_vless_placeholder,
                    ),
                    helper = stringResource(
                        if (expectsSubscription) R.string.profile_subscription_input_hint
                        else R.string.profile_vless_input_hint,
                    ),
                    error = contextError,
                    success = parsed?.takeIf { parsedMatchesEditor }?.let { result ->
                        if (expectsSubscription) {
                            stringResource(R.string.subscription_profile_host, result.profile.server)
                        } else {
                            stringResource(
                                R.string.key_detected_server,
                                result.profile.server,
                                result.profile.port,
                            )
                        }
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
                        onClick = ::dismissEditor,
                        enabled = !vlessSaving,
                        style = ButtonStyle.SECONDARY,
                        modifier = Modifier.weight(1f),
                    )
                    DetourButton(
                        text = stringResource(
                            if (vlessSaving) R.string.vless_saving else R.string.btn_save,
                        ),
                        enabled = parsed != null && parsedMatchesEditor && !vlessSaving,
                        onClick = {
                            val value = field.trim()
                            val parsedProfile = parsed?.profile ?: return@DetourButton
                            val fallback = if (parsedProfile.isSubscription) {
                                subscriptionFallbackTitle
                            } else {
                                vlessFallbackTitle
                            }
                            val existing = editingId?.let { id -> vlessItems.firstOrNull { it.id == id } }
                            val preservedNode = existing?.selectedNode?.takeIf {
                                parsedProfile.isSubscription && existing.uri == value
                            }
                            val key = VlessKey(
                                id = editingId ?: UUID.randomUUID().toString(),
                                name = parsedProfile.name.ifBlank { parsedProfile.server.ifBlank { fallback } },
                                uri = value,
                                selectedNode = preservedNode,
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

@Composable
private fun ProfileKeyList(
    items: List<VlessKey>,
    kind: VpnProfileKind,
    activeVpn: VpnProfileKind,
    activeVlessId: String?,
    onEdit: (VlessKey) -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyProfilesCard()
        return
    }

    DetourCard(
        Modifier
            .padding(horizontal = Spacing.space16)
            .selectableGroup(),
    ) {
        items.forEachIndexed { index, key ->
            val profile = remember(key.uri) { parsedProfile(key) }
            val selected = activeVpn == kind && key.id == activeVlessId
            val title = profile?.name?.ifBlank { profile.server } ?: key.name
            val subtitle = when {
                profile == null -> "—"
                profile.isSubscription -> stringResource(R.string.subscription_profile_host, profile.server)
                else -> "${profile.server}:${profile.port}"
            }
            CompactProfileRow(
                title = title,
                subtitle = subtitle,
                selected = selected,
                busy = false,
                editDescription = stringResource(R.string.key_edit),
                deleteDescription = stringResource(R.string.key_delete),
                onEdit = { onEdit(key) },
                onDelete = { onDelete(key.id) },
                onClick = { if (!selected) onSelect(key.id) },
            )
            if (index < items.lastIndex) GroupDivider(startInset = 52)
        }
    }
}

@Composable
private fun WarpProfileList(
    profile: WarpProfile?,
    selected: Boolean,
    importing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    if (profile == null) {
        EmptyProfilesCard()
        return
    }

    DetourCard(
        Modifier
            .padding(horizontal = Spacing.space16)
            .selectableGroup(),
    ) {
        CompactProfileRow(
            title = profile.name,
            subtitle = stringResource(R.string.warp_subtitle, profile.proxies.size),
            selected = selected,
            busy = importing,
            editDescription = stringResource(R.string.warp_replace),
            deleteDescription = stringResource(R.string.warp_delete),
            onEdit = onEdit,
            onDelete = onDelete,
            onClick = { if (!selected) onClick() },
        )
    }
}

@Composable
private fun CompactProfileRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    busy: Boolean,
    editDescription: String,
    deleteDescription: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val c = detourColors
    Row(
        Modifier
            .fillMaxWidth()
            .detourSelectable(
                selected = selected,
                onClick = onClick,
                idleColor = if (selected) c.accentSoft else Color.Transparent,
                pressedColor = if (selected) c.accentSoft else c.surfaceSelected,
                pressScale = Motion.PRESS_RADIO,
            )
            .heightIn(min = 64.dp)
            .padding(start = Spacing.space16, top = Spacing.space8, bottom = Spacing.space8, end = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        Column(
            Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Spacing.space2),
            )
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = Spacing.space12).size(18.dp),
                strokeWidth = 2.dp,
                color = c.accent,
            )
        } else {
            DetourIconButton(onClick = onEdit, size = 36) {
                Icon(
                    painterResource(R.drawable.ic_edit),
                    contentDescription = "$editDescription: $title",
                    tint = c.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            DetourIconButton(onClick = onDelete, size = 36) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "$deleteDescription: $title",
                    tint = c.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyProfilesCard() {
    val c = detourColors
    DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
        Text(
            stringResource(R.string.profile_empty),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(Spacing.space16),
        )
    }
}

@Composable
private fun ProfileOperationNotice(status: WarpImportStatus) {
    val c = detourColors
    val importing = status == WarpImportStatus.IMPORTING
    val error = status == WarpImportStatus.NO_COMPATIBLE_PROXIES || status == WarpImportStatus.ERROR
    if (!importing && !error) return

    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.space16)
            .fillMaxWidth()
            .background(if (error) c.errorSoft else c.surfaceSoft, AppShapes.small)
            .border(1.dp, if (error) c.error.copy(alpha = 0.30f) else c.border, AppShapes.small)
            .padding(Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (importing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = c.accent,
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = c.error,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = when (status) {
                WarpImportStatus.IMPORTING -> stringResource(R.string.warp_importing)
                WarpImportStatus.NO_COMPATIBLE_PROXIES -> stringResource(R.string.warp_invalid)
                WarpImportStatus.ERROR -> stringResource(R.string.warp_import_error)
                WarpImportStatus.IDLE -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (error) c.textPrimary else c.textSecondary,
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        )
    }
}
