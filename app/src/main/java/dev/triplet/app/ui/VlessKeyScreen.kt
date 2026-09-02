package dev.triplet.app.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VlessProfile
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.core.WarpProfile
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
    var field by rememberSaveable { mutableStateOf("") }
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

    val warpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importWarpDocument(it.toString()) }
    }

    LaunchedEffect(vlessSaveStatus, sheetState) {
        if (vlessSaveStatus == VlessSaveStatus.SAVED) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            runCatching { sheetState.hide() }
            showEditor = false
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
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
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
                        height = 58,
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
            DetourBrandedHeader(stringResource(R.string.app_name), onBack)

            DetourContentColumn {
                Text(
                    text = stringResource(R.string.key_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        start = Spacing.space20,
                        end = Spacing.space20,
                        top = Spacing.space12,
                    ),
                )

                Spacer(Modifier.height(Spacing.space24))
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
                Spacer(Modifier.height(Spacing.space24))

                when (selectedTab) {
                    0 -> ProfileKeyCards(
                        title = stringResource(R.string.protocol_vless),
                        items = groups.vless,
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
                        ProfileKeyCards(
                            title = stringResource(R.string.subscription_profile_section),
                            items = groups.subscriptions,
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
                            Spacer(Modifier.height(Spacing.space24))
                            SubscriptionRuntimeSection()
                        }
                    }
                    2 -> WarpProfileCardSection(
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
                    Spacer(Modifier.height(Spacing.space16))
                    ProfileOperationNotice(warpImportStatus)
                }
                Spacer(Modifier.height(Spacing.space24))
            }
        }
    }

    if (showEditor) {
        ModalBottomSheet(
            onDismissRequest = { if (!vlessSaving) showEditor = false },
            sheetState = sheetState,
            containerColor = c.background,
            contentColor = c.textPrimary,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
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
                            val key = VlessKey(
                                editingId ?: UUID.randomUUID().toString(),
                                parsedProfile.name.ifBlank { parsedProfile.server.ifBlank { fallback } },
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

@Composable
private fun ProfileKeyCards(
    title: String,
    items: List<VlessKey>,
    activeVpn: VpnProfileKind,
    activeVlessId: String?,
    onEdit: (VlessKey) -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val c = detourColors
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = c.accent,
        modifier = Modifier.padding(horizontal = Spacing.space20),
    )
    Spacer(Modifier.height(Spacing.space12))

    if (items.isEmpty()) {
        EmptyProfilesCard()
        return
    }

    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        items.forEach { key ->
            val profile = remember(key.uri) { parsedProfile(key) }
            val selected = activeVpn != VpnProfileKind.WARP && key.id == activeVlessId
            ProfileCard(
                title = profile?.name?.ifBlank { profile.server } ?: key.name,
                server = profile?.server ?: "—",
                type = if (profile?.isSubscription == true) {
                    stringResource(R.string.protocol_vless)
                } else {
                    stringResource(R.string.profile_reality)
                },
                iconRes = if (profile?.isSubscription == true) R.drawable.ic_globe else R.drawable.ic_lock,
                selected = selected,
                onEdit = { onEdit(key) },
                onDelete = { onDelete(key.id) },
                onClick = { if (!selected) onSelect(key.id) },
            )
        }
    }
}

@Composable
private fun WarpProfileCardSection(
    profile: WarpProfile?,
    selected: Boolean,
    importing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val c = detourColors
    Text(
        text = stringResource(R.string.protocol_warp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = c.accent,
        modifier = Modifier.padding(horizontal = Spacing.space20),
    )
    Spacer(Modifier.height(Spacing.space12))

    if (profile == null) {
        EmptyProfilesCard()
        return
    }

    ProfileCard(
        title = profile.name,
        server = stringResource(R.string.warp_subtitle, profile.proxies.size),
        type = stringResource(R.string.profile_amneziawg),
        iconRes = R.drawable.ic_globe,
        selected = selected,
        busy = importing,
        onEdit = onEdit,
        onDelete = onDelete,
        onClick = { if (!selected) onClick() },
    )
}

@Composable
private fun ProfileCard(
    title: String,
    server: String,
    type: String,
    iconRes: Int,
    selected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    busy: Boolean = false,
) {
    val c = detourColors
    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.space16)
            .fillMaxWidth()
            .clip(AppShapes.medium)
            .background(c.surface)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) c.accentBorder else c.border,
                AppShapes.medium,
            )
            .detourSelectable(
                selected = selected,
                onClick = onClick,
                idleColor = if (selected) c.accentSoft.copy(alpha = 0.28f) else Color.Transparent,
                pressedColor = if (selected) c.accentSoft else c.surfaceSelected,
                pressScale = Motion.PRESS_RADIO,
            )
            .padding(Spacing.space16),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileIconTile(iconRes = iconRes, selected = selected)
            Column(
                modifier = Modifier
                    .padding(start = Spacing.space12)
                    .weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Row(
                        modifier = Modifier
                            .padding(top = Spacing.space8)
                            .background(c.activeSoft, PillShape)
                            .border(1.dp, c.activeBorder, PillShape)
                            .padding(horizontal = Spacing.space8, vertical = Spacing.space4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(c.activeStrong, CircleShape),
                        )
                        Text(
                            text = stringResource(R.string.profile_selected),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.activeStrong,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = Spacing.space4),
                        )
                    }
                }
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = c.accent,
                )
            } else {
                DetourIconButton(onClick = onEdit, size = 40) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = stringResource(R.string.key_edit),
                        tint = c.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DetourIconButton(onClick = onDelete, size = 40) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.key_delete),
                        tint = c.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.space16))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.divider),
        )
        Spacer(Modifier.height(Spacing.space12))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space16),
        ) {
            ProfileMeta(
                label = stringResource(R.string.profile_server_label),
                value = server,
                iconRes = R.drawable.ic_globe,
                modifier = Modifier.weight(1f),
            )
            ProfileMeta(
                label = stringResource(R.string.profile_type_label),
                value = type,
                iconRes = R.drawable.ic_lock,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProfileIconTile(iconRes: Int, selected: Boolean) {
    val c = detourColors
    Box(
        modifier = Modifier
            .size(54.dp)
            .background(if (selected) c.accentSoft else c.surfaceSoft, AppShapes.small)
            .border(
                1.dp,
                if (selected) c.accentBorder.copy(alpha = 0.72f) else c.border,
                AppShapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (selected) c.accent else c.textSecondary,
            modifier = Modifier.size(27.dp),
        )
    }
}

@Composable
private fun ProfileMeta(
    label: String,
    value: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = c.textMuted,
        )
        Row(
            modifier = Modifier.padding(top = Spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Spacing.space8),
            )
        }
    }
}

@Composable
private fun EmptyProfilesCard() {
    val c = detourColors
    DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.space20),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetourIconTile(R.drawable.ic_lock)
            Text(
                stringResource(R.string.profile_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier
                    .padding(start = Spacing.space12)
                    .weight(1f),
            )
        }
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
