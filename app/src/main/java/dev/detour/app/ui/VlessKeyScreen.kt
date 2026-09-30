package dev.detour.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.detour.app.R
import dev.detour.app.core.ParseResult
import dev.detour.app.core.QrImageDecoder
import dev.detour.app.core.VlessKey
import dev.detour.app.core.VlessKeyParser
import dev.detour.app.core.VlessProfile
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.core.WarpProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val PROFILES_SCREEN_TEST_TAG = "profiles_screen"

private data class ProfileGroups(
    val vless: List<VlessKey>,
    val subscriptions: List<VlessKey>,
    val wireGuard: List<WarpProfile>,
)

private const val MAX_PROFILE_NAME_CHARS = 64

internal fun parsedProfile(key: VlessKey): VlessProfile? =
    (VlessKeyParser.parse(key.uri) as? ParseResult.Ok)?.profile

private fun cleanProfileName(raw: String): String =
    raw.filter { it.code >= 0x20 && it.code != 0x7f }.take(MAX_PROFILE_NAME_CHARS)

internal fun autoProfileName(profile: VlessProfile, fallback: String = ""): String =
    profile.name.ifBlank { profile.server.ifBlank { fallback } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlessKeyScreen(viewModel: ProfilesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val c = detourColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vlessItems = state.vlessItems
    val wireGuardProfiles = state.wireGuardProfiles
    val activeWireGuardId = state.activeWireGuardId
    val activeVpn = state.activeVpn
    val activeVlessId = state.activeVlessId
    val warpImportStatus = state.warpImportStatus
    val warpImporting = warpImportStatus == WarpImportStatus.IMPORTING
    val vlessSaveStatus = state.vlessSaveStatus
    val vlessSaving = vlessSaveStatus == VlessSaveStatus.SAVING
    val importBusy = warpImporting || vlessSaving
    val vlessFallbackTitle = stringResource(R.string.protocol_vless)
    val subscriptionFallbackTitle = stringResource(R.string.subscription_profile_section)

    val groups = remember(vlessItems, wireGuardProfiles) {
        ProfileGroups(
            vless = vlessItems.filter { parsedProfile(it)?.isSubscription != true },
            subscriptions = vlessItems.filter { parsedProfile(it)?.isSubscription == true },
            wireGuard = wireGuardProfiles,
        )
    }

    var showAddMenu by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingSubscription by rememberSaveable { mutableStateOf(false) }
    var nameField by rememberSaveable { mutableStateOf("") }
    var editingWireGuardId by rememberSaveable { mutableStateOf<String?>(null) }
    var wireGuardNameField by rememberSaveable { mutableStateOf("") }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var suppressWarpNotice by rememberSaveable { mutableStateOf(false) }
    var replacingWireGuardId by rememberSaveable { mutableStateOf<String?>(null) }
    var qrImportFailed by rememberSaveable { mutableStateOf(false) }
    // Credential drafts deliberately stay process-memory-only. Recreating the
    // Activity must not serialize a VLESS/subscription URI into saved instance state.
    var field by remember { mutableStateOf("") }

    val parse = remember(field) {
        field.trim().takeIf { it.isNotBlank() }?.let(VlessKeyParser::parse)
    }
    val parsed = parse as? ParseResult.Ok
    val directVlessSource = field.trim().startsWith("vless://", ignoreCase = true)
    // Editing keeps the profile's type; a new link is typed by its content.
    val subscriptionEditor = if (editingId != null) {
        editingSubscription
    } else {
        parsed?.profile?.isSubscription ?: field.trim().startsWith("https://", ignoreCase = true)
    }
    val parsedMatchesEditor = when {
        parsed == null -> false
        editingId != null -> parsed.profile.isSubscription == editingSubscription
        else -> parsed.profile.isSubscription || directVlessSource
    }

    val currentVlessSaving = rememberUpdatedState(vlessSaving)
    val confirmEditorSheetValueChange = remember {
        { target: SheetValue -> !currentVlessSaving.value || target != SheetValue.Hidden }
    }
    val editorSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmEditorSheetValueChange,
    )
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val wireGuardSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val contentResolver = LocalContext.current.contentResolver

    val warpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val replaceId = replacingWireGuardId
        replacingWireGuardId = null
        uri?.let {
            suppressWarpNotice = false
            viewModel.importWarpDocument(it.toString(), replaceProfileId = replaceId)
        }
    }

    LaunchedEffect(vlessSaveStatus, editorSheetState) {
        if (vlessSaveStatus == VlessSaveStatus.SAVED) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            runCatching { editorSheetState.hide() }
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

    fun beginEditor(key: VlessKey? = null, subscription: Boolean = false, prefill: String = "") {
        viewModel.clearVlessSaveError()
        editingId = key?.id
        field = key?.uri ?: prefill
        nameField = key?.let { k -> k.name.ifBlank { parsedProfile(k)?.let(::autoProfileName).orEmpty() } }.orEmpty()
        editingSubscription = key?.let { parsedProfile(it)?.isSubscription == true } ?: subscription
        showAddMenu = false
        showEditor = true
    }

    fun dismissEditor() {
        scope.launch {
            runCatching { editorSheetState.hide() }
            showEditor = false
            field = ""
        }
    }

    /**
     * One entry point for links from the clipboard or a QR code. The link type
     * decides the destination; a malformed VLESS/subscription link opens the
     * editor prefilled so the user sees exactly what is wrong with it.
     */
    fun importLink(rawInput: String) {
        val raw = rawInput.trim().replace("\r", "").replace("\n", "")
        showAddMenu = false
        qrImportFailed = false
        viewModel.clearVlessSaveError()
        suppressWarpNotice = false
        scope.launch {
            val parsedLink = withContext(Dispatchers.Default) { VlessKeyParser.parse(raw) }
            val amneziaInvite = raw.startsWith("vpn://", ignoreCase = true)
            when {
                parsedLink is ParseResult.Ok && !(amneziaInvite && parsedLink.profile.isSubscription) -> {
                    suppressWarpNotice = true
                    val profile = parsedLink.profile
                    val fallback = if (profile.isSubscription) subscriptionFallbackTitle else vlessFallbackTitle
                    viewModel.saveVless(
                        VlessKey(
                            id = UUID.randomUUID().toString(),
                            name = autoProfileName(profile, fallback),
                            uri = raw,
                        ),
                        isNew = true,
                    )
                }
                raw.startsWith("vless://", ignoreCase = true) ||
                    raw.startsWith("https://", ignoreCase = true) ->
                    beginEditor(subscription = raw.startsWith("https://", ignoreCase = true), prefill = raw)
                else -> viewModel.importWarpInvite(raw)
            }
        }
    }

    fun pasteFromClipboard() {
        scope.launch {
            val raw = clipboard.getClipEntry()
                ?.clipData
                ?.getItemAt(0)
                ?.text
                ?.toString()
                .orEmpty()
            importLink(raw)
        }
    }

    val qrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { QrImageDecoder.decode(contentResolver, uri) }
                if (text == null) {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    qrImportFailed = true
                } else {
                    importLink(text)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .testTag(PROFILES_SCREEN_TEST_TAG)
            .fillMaxSize(),
        containerColor = c.background,
        bottomBar = {
            if (!showEditor && editingWireGuardId == null && !importBusy) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
                ) {
                    DetourButton(
                        text = stringResource(R.string.profile_add_action),
                        onClick = { showAddMenu = true },
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

                val hasProfiles = groups.vless.isNotEmpty() ||
                    groups.subscriptions.isNotEmpty() ||
                    groups.wireGuard.isNotEmpty()

                if (!hasProfiles) {
                    EmptyProfilesCard()
                } else {
                    if (groups.vless.isNotEmpty()) {
                        ProfileSectionTitle(stringResource(R.string.profile_section_vless))
                        ProfileKeyList(
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
                    }

                    if (groups.subscriptions.isNotEmpty()) {
                        if (groups.vless.isNotEmpty()) Spacer(Modifier.height(Spacing.space16))
                        ProfileSectionTitle(stringResource(R.string.profile_section_subscriptions))
                        ProfileKeyList(
                            items = groups.subscriptions,
                            kind = VpnProfileKind.SUBSCRIPTION,
                            activeVpn = activeVpn,
                            activeVlessId = activeVlessId,
                            onEdit = { beginEditor(it, subscription = true) },
                            onDelete = viewModel::deleteVless,
                            onSelect = { keyId ->
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                viewModel.selectVless(keyId)
                            },
                        )
                        val activeSubscription = groups.subscriptions.firstOrNull {
                            it.id == activeVlessId && activeVpn == VpnProfileKind.SUBSCRIPTION
                        }
                        AnimatedVisibility(
                            visible = activeSubscription != null,
                            enter = Motion.reveal,
                            exit = Motion.conceal,
                        ) {
                            Column {
                                Spacer(Modifier.height(Spacing.space12))
                                SubscriptionRuntimeSection()
                            }
                        }
                    }

                    if (groups.wireGuard.isNotEmpty()) {
                        if (groups.vless.isNotEmpty() || groups.subscriptions.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.space16))
                        }
                        ProfileSectionTitle(stringResource(R.string.profile_section_wireguard))
                        WireGuardProfileList(
                            profiles = groups.wireGuard,
                            activeWireGuardId = activeWireGuardId,
                            activeVpn = activeVpn,
                            importing = warpImporting,
                            onEdit = { profileId ->
                                wireGuardNameField = groups.wireGuard.firstOrNull { it.id == profileId }?.displayName.orEmpty()
                                editingWireGuardId = profileId
                            },
                            onDelete = viewModel::deleteWarp,
                            onClick = { profileId ->
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                viewModel.selectWarp(profileId)
                            },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !suppressWarpNotice && warpImportStatus != WarpImportStatus.IDLE,
                    enter = Motion.reveal,
                    exit = Motion.conceal,
                ) {
                    Column {
                        Spacer(Modifier.height(Spacing.space12))
                        ProfileOperationNotice(warpImportStatus)
                    }
                }
                AnimatedVisibility(
                    visible = !showEditor && vlessSaveStatus == VlessSaveStatus.ERROR,
                    enter = Motion.reveal,
                    exit = Motion.conceal,
                ) {
                    Column {
                        Spacer(Modifier.height(Spacing.space12))
                        ProfileImportErrorNotice()
                    }
                }
                AnimatedVisibility(visible = qrImportFailed, enter = Motion.reveal, exit = Motion.conceal) {
                    Column {
                        Spacer(Modifier.height(Spacing.space12))
                        ProfileImportErrorNotice(stringResource(R.string.profile_qr_not_found))
                    }
                }
                Spacer(Modifier.height(Spacing.space24))
            }
        }
    }

    if (showAddMenu) {
        ModalBottomSheet(
            onDismissRequest = { showAddMenu = false },
            sheetState = addSheetState,
            containerColor = c.background,
            contentColor = c.textPrimary,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space16),
            ) {
                Text(
                    text = stringResource(R.string.profile_add_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space12),
                )
                DetourCard {
                    DetourNavigationRow(
                        title = stringResource(R.string.profile_add_paste),
                        subtitle = stringResource(R.string.profile_add_paste_hint),
                        iconRes = R.drawable.ic_clipboard,
                        onClick = ::pasteFromClipboard,
                    )
                    GroupDivider(startInset = NavigationRowDividerInset)
                    DetourNavigationRow(
                        title = stringResource(R.string.profile_add_qr),
                        subtitle = stringResource(R.string.profile_add_qr_hint),
                        iconRes = R.drawable.ic_qr,
                        onClick = {
                            showAddMenu = false
                            qrLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                    GroupDivider(startInset = NavigationRowDividerInset)
                    DetourNavigationRow(
                        title = stringResource(R.string.profile_add_link),
                        subtitle = stringResource(R.string.profile_add_link_hint),
                        iconRes = R.drawable.ic_link,
                        onClick = { beginEditor(subscription = false) },
                    )
                    GroupDivider(startInset = NavigationRowDividerInset)
                    DetourNavigationRow(
                        title = stringResource(R.string.profile_import_file),
                        subtitle = stringResource(R.string.profile_import_file_hint),
                        iconRes = R.drawable.ic_import,
                        onClick = {
                            showAddMenu = false
                            suppressWarpNotice = false
                            replacingWireGuardId = null
                            warpLauncher.launch(arrayOf("*/*"))
                        },
                    )
                }
                Spacer(Modifier.navigationBarsPadding().height(Spacing.space16))
            }
        }
    }

    val wireGuardEditing = editingWireGuardId?.let { id -> groups.wireGuard.firstOrNull { it.id == id } }
    if (editingWireGuardId != null && wireGuardEditing == null) editingWireGuardId = null
    if (wireGuardEditing != null) {
        val closeWireGuardSheet: () -> Unit = {
            scope.launch {
                runCatching { wireGuardSheetState.hide() }
                editingWireGuardId = null
            }
        }
        ModalBottomSheet(
            onDismissRequest = { editingWireGuardId = null },
            sheetState = wireGuardSheetState,
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
                    DetourIconTile(iconRes = R.drawable.ic_lock, selected = true)
                    Text(
                        text = stringResource(R.string.profile_edit_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        modifier = Modifier.padding(start = Spacing.space12),
                    )
                }
                DetourInputField(
                    value = wireGuardNameField,
                    onValueChange = { value -> wireGuardNameField = cleanProfileName(value) },
                    label = stringResource(R.string.profile_name_label),
                    placeholder = wireGuardEditing.displayName,
                )
                Spacer(Modifier.height(Spacing.space16))
                DetourButton(
                    text = stringResource(R.string.profile_replace_config),
                    onClick = {
                        replacingWireGuardId = wireGuardEditing.id
                        editingWireGuardId = null
                        suppressWarpNotice = false
                        warpLauncher.launch(arrayOf("*/*"))
                    },
                    style = ButtonStyle.SECONDARY,
                )
                Spacer(Modifier.height(Spacing.space12))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                ) {
                    DetourButton(
                        text = stringResource(R.string.key_cancel),
                        onClick = closeWireGuardSheet,
                        style = ButtonStyle.SECONDARY,
                        modifier = Modifier.weight(1f),
                    )
                    DetourButton(
                        text = stringResource(R.string.btn_save),
                        enabled = wireGuardNameField.isNotBlank(),
                        onClick = {
                            viewModel.renameWireGuard(wireGuardEditing.id, wireGuardNameField)
                            closeWireGuardSheet()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.navigationBarsPadding().height(Spacing.space16))
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
            sheetState = editorSheetState,
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
                        iconRes = if (subscriptionEditor) R.drawable.ic_globe else R.drawable.ic_lock,
                        selected = true,
                    )
                    Text(
                        text = when {
                            editingId != null -> stringResource(R.string.vless_edit_title)
                            else -> stringResource(R.string.profile_add_link)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        modifier = Modifier.padding(start = Spacing.space12),
                    )
                }

                val contextError = when {
                    parse is ParseResult.Err -> stringResource(
                        if (subscriptionEditor) R.string.profile_subscription_invalid
                        else R.string.profile_vless_direct_invalid,
                    )
                    parsed != null && !parsedMatchesEditor -> stringResource(
                        if (subscriptionEditor) R.string.profile_subscription_wrong_type
                        else R.string.profile_vless_direct_invalid,
                    )
                    vlessSaveStatus == VlessSaveStatus.ERROR -> stringResource(R.string.vless_save_error)
                    else -> null
                }

                DetourInputField(
                    value = nameField,
                    onValueChange = { value -> nameField = cleanProfileName(value) },
                    label = stringResource(R.string.profile_name_label),
                    placeholder = parsed?.takeIf { parsedMatchesEditor }?.profile?.let { autoProfileName(it, "") }
                        ?: stringResource(R.string.profile_name_label),
                    helper = stringResource(R.string.profile_name_hint),
                    enabled = !vlessSaving,
                )
                Spacer(Modifier.height(Spacing.space12))

                DetourInputField(
                    value = field,
                    onValueChange = { value ->
                        viewModel.clearVlessSaveError()
                        field = value.replace("\r", "").replace("\n", "")
                    },
                    label = stringResource(
                        when {
                            editingId == null -> R.string.profile_link_input_label
                            subscriptionEditor -> R.string.profile_subscription_input_label
                            else -> R.string.profile_vless_direct_input_label
                        },
                    ),
                    placeholder = stringResource(
                        when {
                            editingId == null -> R.string.profile_link_placeholder
                            subscriptionEditor -> R.string.profile_subscription_placeholder
                            else -> R.string.profile_vless_direct_placeholder
                        },
                    ),
                    helper = stringResource(
                        when {
                            editingId == null -> R.string.profile_add_link_hint
                            subscriptionEditor -> R.string.profile_subscription_input_hint
                            else -> R.string.profile_vless_direct_input_hint
                        },
                    ),
                    error = contextError,
                    success = parsed?.takeIf { parsedMatchesEditor }?.let { result ->
                        if (subscriptionEditor) {
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
                                name = nameField.trim().ifBlank { autoProfileName(parsedProfile, fallback) },
                                uri = value,
                                selectedNode = preservedNode,
                            )
                            suppressWarpNotice = true
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
