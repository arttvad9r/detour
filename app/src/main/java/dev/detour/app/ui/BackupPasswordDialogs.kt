package dev.detour.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.detour.app.R

internal const val MIN_BACKUP_PASSWORD_LENGTH = 8

@Composable
private fun BackupDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = detourColors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.space24),
            contentAlignment = Alignment.Center,
        ) {
            DetourCard(Modifier.widthIn(max = 420.dp)) {
                Column(Modifier.padding(Spacing.space20)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = c.textPrimary,
                    )
                    Spacer(Modifier.height(Spacing.space16))
                    content()
                }
            }
        }
    }
}

/**
 * Asks whether to protect an export with a password. [onConfirm] receives the
 * password, or null to export without encryption.
 */
@Composable
fun BackupExportDialog(
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val tooShort = password.isNotEmpty() && password.length < MIN_BACKUP_PASSWORD_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != password
    val canEncrypt = password.length >= MIN_BACKUP_PASSWORD_LENGTH && password == confirm

    BackupDialogFrame(stringResource(R.string.backup_encrypt_title), onDismiss) {
        Text(
            text = stringResource(R.string.backup_encrypt_message),
            style = MaterialTheme.typography.bodyMedium,
            color = detourColors.textSecondary,
        )
        Spacer(Modifier.height(Spacing.space16))
        DetourInputField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.backup_password_label),
            placeholder = "",
            password = true,
            error = if (tooShort) stringResource(R.string.backup_password_short, MIN_BACKUP_PASSWORD_LENGTH) else null,
        )
        Spacer(Modifier.height(Spacing.space12))
        DetourInputField(
            value = confirm,
            onValueChange = { confirm = it },
            label = stringResource(R.string.backup_password_confirm_label),
            placeholder = "",
            password = true,
            error = if (mismatch) stringResource(R.string.backup_password_mismatch) else null,
        )
        Spacer(Modifier.height(Spacing.space8))
        Text(
            text = stringResource(R.string.backup_password_warning),
            style = MaterialTheme.typography.bodySmall,
            color = detourColors.textMuted,
        )
        Spacer(Modifier.height(Spacing.space20))
        DetourButton(
            text = stringResource(R.string.backup_export_encrypted),
            onClick = { onConfirm(password) },
            enabled = canEncrypt,
        )
        Spacer(Modifier.height(Spacing.space8))
        DetourButton(
            text = stringResource(R.string.backup_export_plain),
            onClick = { onConfirm(null) },
            style = ButtonStyle.SECONDARY,
        )
        Spacer(Modifier.height(Spacing.space8))
        DetourButton(
            text = stringResource(R.string.key_cancel),
            onClick = onDismiss,
            style = ButtonStyle.SECONDARY,
        )
    }
}

@Composable
fun BackupImportPasswordDialog(
    wrongPassword: Boolean,
    busy: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }

    BackupDialogFrame(stringResource(R.string.backup_import_password_title), onDismiss) {
        Text(
            text = stringResource(R.string.backup_import_password_message),
            style = MaterialTheme.typography.bodyMedium,
            color = detourColors.textSecondary,
        )
        Spacer(Modifier.height(Spacing.space16))
        DetourInputField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.backup_password_label),
            placeholder = "",
            password = true,
            enabled = !busy,
            error = if (wrongPassword) stringResource(R.string.backup_wrong_password) else null,
        )
        Spacer(Modifier.height(Spacing.space20))
        DetourButton(
            text = stringResource(R.string.backup_import_decrypt),
            onClick = { onConfirm(password) },
            enabled = password.isNotEmpty() && !busy,
        )
        Spacer(Modifier.height(Spacing.space8))
        DetourButton(
            text = stringResource(R.string.key_cancel),
            onClick = onDismiss,
            style = ButtonStyle.SECONDARY,
        )
    }
}
