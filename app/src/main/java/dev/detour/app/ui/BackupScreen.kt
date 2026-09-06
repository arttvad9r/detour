package dev.detour.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.detour.app.R

private enum class BackupNoticeTone { SUCCESS, ERROR }
private const val BackupActionDividerInset = 70

@Composable
fun BackupScreen(viewModel: BackupViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val c = detourColors
    val status by viewModel.status.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    LaunchedEffect(viewModel) {
        viewModel.feedback.collect { feedback ->
            haptics.performHapticFeedback(
                if (feedback == BackupFeedback.CONFIRM) {
                    HapticFeedbackType.Confirm
                } else {
                    HapticFeedbackType.Reject
                },
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportDocument(it.toString()) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importDocument(it.toString()) }
    }

    val statusText = when (status) {
        BackupStatus.EXPORTED -> stringResource(R.string.backup_exported)
        BackupStatus.BAD_FILE -> stringResource(R.string.backup_bad_file)
        BackupStatus.IMPORTED -> stringResource(R.string.backup_imported_reconnect)
        BackupStatus.ERROR -> stringResource(R.string.backup_error)
        null -> ""
    }
    val statusIsError = status == BackupStatus.BAD_FILE || status == BackupStatus.ERROR
    val busy = operation != null

    Column(
        modifier.fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .detourHighRefresh(scrollState.isScrollInProgress),
    ) {
        DetourBrandedHeader(stringResource(R.string.backup_title), onBack)

        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space8))
            DetourFeatureSummary(
                iconRes = R.drawable.ic_export,
                title = stringResource(R.string.backup_hint_title),
                subtitle = stringResource(R.string.backup_note),
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )

            Spacer(Modifier.height(Spacing.space12))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                ActionRow(
                    label = stringResource(
                        if (operation == BackupOperation.EXPORT) R.string.backup_exporting
                        else R.string.backup_export,
                    ),
                    iconRes = R.drawable.ic_export,
                    accent = true,
                    enabled = !busy,
                    loading = operation == BackupOperation.EXPORT,
                ) {
                    exportLauncher.launch("detour-backup.json")
                }
                GroupDivider(startInset = BackupActionDividerInset)
                ActionRow(
                    label = stringResource(
                        if (operation == BackupOperation.IMPORT) R.string.backup_importing
                        else R.string.backup_import,
                    ),
                    iconRes = R.drawable.ic_import,
                    accent = false,
                    enabled = !busy,
                    loading = operation == BackupOperation.IMPORT,
                ) {
                    importLauncher.launch(arrayOf("application/json", "text/*"))
                }
            }

            AnimatedVisibility(
                visible = statusText.isNotEmpty(),
                enter = fadeIn(tween(Motion.CONTENT_IN_MS)),
                exit = fadeOut(tween(Motion.CONTENT_OUT_MS)),
            ) {
                Column {
                    Spacer(Modifier.height(Spacing.space12))
                    BackupNotice(
                        text = statusText,
                        iconRes = if (statusIsError) R.drawable.ic_warning else R.drawable.ic_check,
                        tone = if (statusIsError) BackupNoticeTone.ERROR else BackupNoticeTone.SUCCESS,
                        modifier = Modifier.padding(horizontal = Spacing.space16),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.space24))
        }
    }
}

@Composable
private fun BackupNotice(
    text: String,
    iconRes: Int,
    tone: BackupNoticeTone,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val container = when (tone) {
        BackupNoticeTone.SUCCESS -> c.activeSoft
        BackupNoticeTone.ERROR -> c.errorSoft
    }
    val border = when (tone) {
        BackupNoticeTone.SUCCESS -> c.activeBorder
        BackupNoticeTone.ERROR -> c.error.copy(alpha = 0.34f)
    }
    val iconTint = when (tone) {
        BackupNoticeTone.SUCCESS -> c.activeStrong
        BackupNoticeTone.ERROR -> c.error
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container, AppShapes.small)
            .border(1.dp, border, AppShapes.small)
            .padding(horizontal = Spacing.space12, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(c.surface.copy(alpha = 0.72f), AppShapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = c.textPrimary,
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    iconRes: Int,
    accent: Boolean,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val c = detourColors
    val row = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
    val interactiveRow = if (enabled) {
        row.detourClickable(
            onClick = onClick,
            role = Role.Button,
            pressedColor = c.surfaceSelected.copy(alpha = 0.38f),
            pressScale = Motion.PRESS_ROW,
        )
    } else {
        row.semantics {
            disabled()
            role = Role.Button
        }
    }

    Row(
        interactiveRow.padding(horizontal = Spacing.space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetourIconTile(
            iconRes = iconRes,
            selected = accent && enabled,
        )
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = if (enabled) c.textPrimary else c.textMuted,
            modifier = Modifier.padding(start = Spacing.space12).weight(1f),
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = c.accent,
            )
        }
    }
}
