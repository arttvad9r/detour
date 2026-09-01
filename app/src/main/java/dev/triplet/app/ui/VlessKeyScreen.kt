package dev.triplet.app.ui

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
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

private enum class ProfileSheetMode { PICKER, VLESS_EDITOR }

private fun parsedProfile(key: VlessKey): VlessProfile? =
    (VlessKeyParser.parse(key.uri) as? ParseResult.Ok)?.profile

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
    val addDescription = stringResource(R.string.profile_add_title)
    val profileFallbackTitle = stringResource(R.string.profile_add_vless)

    val groups = remember(vlessItems, warpProfile) {
        ProfileGroups(
            vless = vlessItems.filter { parsedProfile(it)?.isSubscription != true },
            subscriptions = vlessItems.filter { parsedProfile(it)?.isSubscription == true },
            warp = warpProfile,
        )
    }

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

    val activeVless = remember(vlessItems, activeVlessId) {
        vlessItems.firstOrNull { it.id == activeVlessId }
    }
    val activeVlessProfile = remember(activeVless?.uri) {
        activeVless?.let(::parsedProfile)
    }

    val summaryIcon = when (activeVpn) {
        VpnProfileKind.VLESS -> R.drawable.ic_lock
        VpnProfileKind.SUBSCRIPTION -> R.drawable.ic_globe
        VpnProfileKind.WARP -> R.drawable.ic_globe
    }
    val summaryTitle = when (activeVpn) {
        VpnProfileKind.VLESS -> activeVlessProfile?.name?.ifBlank { activeVlessProfile.server }
            ?: stringResource(R.string.protocol_vless)
        VpnProfileKind.SUBSCRIPTION -> activeVlessProfile?.name?.ifBlank { activeVlessProfile.server }
            ?: stringResource(R.string.subscription_profile_section)
        VpnProfileKind.WARP -> warpProfile?.name ?: stringResource(R.string.profile_add_warp)
    }
    val summarySubtitle = when (activeVpn) {
        VpnProfileKind.VLESS -> activeVlessProfile?.let { profile ->
            "VLESS · ${profile.server}:${profile.port}"
        } ?: stringResource(R.string.nav_key_sub_none)
        VpnProfileKind.SUBSCRIPTION -> activeVlessProfile?.let { profile ->
            stringResource(R.string.subscription_profile_host, profile.server)
        } ?: stringResource(R.string.nav_key_sub_none)
        VpnProfileKind.WARP -> warpProfile?.let { profile ->
            stringResource(R.string.warp_subtitle, profile.proxies.size)
        } ?: stringResource(R.string.nav_key_sub_none)
    }

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

    val warpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importWarpDocument(it.toString()) }
    }

    Scaffold(
        modifier = modifier.testTag(PROFILES_SCREEN_TEST_TAG).fillMaxSize(),
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
                DetourFeatureSummary(
                    iconRes = summaryIcon,
                    title = summaryTitle,
                    subtitle = summarySubtitle,
                    modifier = Modifier.padding(horizontal = Spacing.space16),
                )
                Spacer(Modifier.height(Spacing.space16))

                AnimatedContent(
                    targetState = groups,
                    transitionSpec = {
                        fadeIn(
                            tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING),
                        ) togetherWith fadeOut(
                            tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING),
                        )
                    },
                    label = "profileList",
                ) { shown ->
                    if (shown.vless.isEmpty() && shown.subscriptions.isEmpty() && shown.warp == null) {
                        EmptyProfilesCard()
                    } else {
                        Column {
                            var hasSection = false
                            if (shown.vless.isNotEmpty()) {
                                ProfileKeySection(
                                    title = stringResource(R.string.protocol_vless),
                                    items = shown.vless,
                                    activeVpn = activeVpn,
                                    activeVlessId = activeVlessId,
                                    onEdit = ::beginVlessEdit,
                                    onDelete = viewModel::deleteVless,
                                    onSelect = { keyId ->
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        viewModel.selectVless(keyId)
                                    },
                                )
                                hasSection = true
                            }
                            if (shown.subscriptions.isNotEmpty()) {
                                if (hasSection) Spacer(Modifier.height(Spacing.space16))
                                ProfileKeySection(
                                    title = stringResource(R.string.subscription_profile_section),
                                    items = shown.subscriptions,
                                    activeVpn = activeVpn,
                                    activeVlessId = activeVlessId,
                                    onEdit = ::beginVlessEdit,
                                    onDelete = viewModel::deleteVless,
                                    onSelect = { keyId ->
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        viewModel.selectVless(keyId)
                                    },
                                )
                                hasSection = true
                            }
                            shown.warp?.let { profile ->
                                if (hasSection) Spacer(Modifier.height(Spacing.space16))
                                ProfileSectionTitle(stringResource(R.string.warp_section_title))
                                Spacer(Modifier.height(Spacing.space8))
                                DetourCard(
                                    Modifier
                                        .padding(horizontal = Spacing.space16)
                                        .selectableGroup(),
                                ) {
                                    WarpRow(
                                        profile = profile,
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
                            }
                        }
                    }
                }

                if (activeVpn == VpnProfileKind.SUBSCRIPTION && activeVlessProfile?.isSubscription == true) {
                    Spacer(Modifier.height(Spacing.space16))
                    SubscriptionRuntimeSection()
                }

                AnimatedVisibility(
                    visible = warpImportStatus != WarpImportStatus.IDLE,
                    enter = fadeIn(tween(Motion.CONTENT_IN_MS, easing = Motion.ENTER_EASING)),
                    exit = fadeOut(tween(Motion.CONTENT_OUT_MS, easing = Motion.EXIT_EASING)),
                ) {
                    Column {
                        Spacer(Modifier.height(Spacing.space12))
                        ProfileOperationNotice(warpImportStatus)
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
            containerColor = c.background,
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
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.space16),
                        ) {
                            Row(
                                Modifier.padding(
                                    start = Spacing.space4,
                                    end = Spacing.space4,
                                    top = Spacing.space4,
                                    bottom = Spacing.space16,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DetourBrandMark(size = 28.dp)
                                Text(
                                    stringResource(R.string.profile_add_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = c.textPrimary,
                                    modifier = Modifier.padding(start = Spacing.space12),
                                )
                            }
                            DetourCard {
                                ProfileTypeRow(
                                    title = stringResource(R.string.profile_add_vless),
                                    subtitle = stringResource(R.string.profile_add_vless_sub),
                                    iconRes = R.drawable.ic_lock,
                                ) {
                                    editingId = null
                                    field = ""
                                    sheetMode = ProfileSheetMode.VLESS_EDITOR
                                }
                                GroupDivider(startInset = 70)
                                ProfileTypeRow(
                                    title = stringResource(R.string.profile_add_warp),
                                    subtitle = stringResource(
                                        if (warpProfile == null) R.string.profile_add_warp_sub
                                        else R.string.profile_replace_warp_sub,
                                    ),
                                    iconRes = R.drawable.ic_globe,
                                ) {
                                    dismissSheet {
                                        warpLauncher.launch(arrayOf("*/*"))
                                    }
                                }
                            }
                            Spacer(Modifier.height(Spacing.space8))
                            TextButton(
                                onClick = { dismissSheet() },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(
                                    stringResource(R.string.key_cancel),
                                    color = c.textSecondary,
                                )
                            }
                            Spacer(Modifier.navigationBarsPadding().height(Spacing.space8))
                        }
                    }

                    ProfileSheetMode.VLESS_EDITOR -> {
                        val parsed = parse as? ParseResult.Ok
                        val editorIsSubscription = parsed?.profile?.isSubscription == true
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
                                    iconRes = if (editorIsSubscription) R.drawable.ic_globe else R.drawable.ic_lock,
                                    selected = true,
                                )
                                Text(
                                    stringResource(
                                        if (editingId == null) R.string.vless_add_title else R.string.vless_edit_title,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = c.textPrimary,
                                    modifier = Modifier.padding(start = Spacing.space12),
                                )
                            }
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
                                success = parsed?.let { result ->
                                    if (result.profile.isSubscription) {
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
                                            parsedProfile?.name?.ifBlank { parsedProfile.server } ?: profileFallbackTitle,
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
private fun EmptyProfilesCard() {
    val c = detourColors
    DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetourIconTile(R.drawable.ic_lock)
            Text(
                stringResource(R.string.profile_empty),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier
                    .padding(start = Spacing.space12)
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun ProfileKeySection(
    title: String,
    items: List<VlessKey>,
    activeVpn: VpnProfileKind,
    activeVlessId: String?,
    onEdit: (VlessKey) -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    ProfileSectionTitle(title)
    Spacer(Modifier.height(Spacing.space8))
    DetourCard(
        Modifier
            .padding(horizontal = Spacing.space16)
            .selectableGroup(),
    ) {
        items.forEachIndexed { index, key ->
            KeyRow(
                key = key,
                selected = activeVpn != VpnProfileKind.WARP && key.id == activeVlessId,
                onEdit = { onEdit(key) },
                onDelete = { onDelete(key.id) },
                onClick = { onSelect(key.id) },
            )
            if (index < items.lastIndex) GroupDivider(startInset = 70)
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

@Composable
private fun ProfileSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = detourColors.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.space20),
    )
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
            shape = PillShape,
            containerColor = c.accent,
            contentColor = c.onAccent,
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.space16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(Spacing.space8))
                Text(
                    stringResource(R.string.key_add),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ProfileTypeRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    val c = detourColors
    Row(
        Modifier
            .fillMaxWidth()
            .detourClickable(
                onClick = onClick,
                role = Role.Button,
                pressedColor = c.surfaceSelected.copy(alpha = 0.42f),
                pressScale = Motion.PRESS_ROW,
            )
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetourIconTile(iconRes = iconRes, selected = true)
        Column(
            Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Spacing.space2),
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
    val profile = remember(key.uri) { parsedProfile(key) }
    val subscription = profile?.isSubscription == true
    Row(
        Modifier
            .fillMaxWidth()
            .detourSelectable(
                selected = selected,
                onClick = { if (!selected) onClick() },
                idleColor = if (selected) c.accentSoft else Color.Transparent,
                pressedColor = if (selected) c.accentSoft else c.surfaceSelected,
                pressScale = Motion.PRESS_RADIO,
            )
            .padding(
                start = Spacing.space16,
                top = Spacing.space12,
                bottom = Spacing.space12,
                end = Spacing.space8,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetourIconTile(
            iconRes = if (subscription) R.drawable.ic_globe else R.drawable.ic_lock,
            selected = selected,
        )
        Column(Modifier.padding(start = Spacing.space12).weight(1f)) {
            Text(
                profile?.name?.ifBlank { profile.server } ?: key.name,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    profile == null -> "—"
                    subscription -> stringResource(R.string.subscription_profile_host, profile.server)
                    else -> "${profile.server}:${profile.port}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            SelectionMark(
                selected = true,
                modifier = Modifier.padding(horizontal = Spacing.space4),
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
        Modifier
            .fillMaxWidth()
            .detourSelectable(
                selected = selected,
                onClick = { if (!selected) onClick() },
                idleColor = if (selected) c.accentSoft else Color.Transparent,
                pressedColor = if (selected) c.accentSoft else c.surfaceSelected,
                pressScale = Motion.PRESS_RADIO,
            )
            .padding(
                start = Spacing.space16,
                top = Spacing.space12,
                bottom = Spacing.space12,
                end = Spacing.space8,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetourIconTile(iconRes = R.drawable.ic_globe, selected = selected)
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
            if (selected) {
                SelectionMark(
                    selected = true,
                    modifier = Modifier.padding(horizontal = Spacing.space4),
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
}
