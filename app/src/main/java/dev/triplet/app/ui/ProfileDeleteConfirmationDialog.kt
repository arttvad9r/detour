package dev.triplet.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.triplet.app.R

@Composable
fun ProfileDeleteConfirmationDialog(
    request: ProfileDeleteRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = detourColors
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = null,
                tint = c.error,
                modifier = Modifier.size(24.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.profile_delete_title),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        if (request.active) {
                            R.string.profile_delete_active_message
                        } else {
                            R.string.profile_delete_message
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
                if (request.failed) {
                    Spacer(Modifier.height(Spacing.space8))
                    Text(
                        text = stringResource(R.string.profile_delete_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(
                        if (request.failed) R.string.action_retry else R.string.key_delete,
                    ),
                    color = c.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.key_cancel),
                    color = c.textSecondary,
                )
            }
        },
        shape = AppShapes.large,
        containerColor = c.surface,
        iconContentColor = c.error,
        titleContentColor = c.textPrimary,
        textContentColor = c.textSecondary,
    )
}
