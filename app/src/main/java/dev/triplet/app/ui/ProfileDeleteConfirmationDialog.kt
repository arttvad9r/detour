package dev.triplet.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
        title = {
            Text(
                text = stringResource(R.string.profile_delete_title),
                color = c.textPrimary,
            )
        },
        text = {
            Text(
                text = stringResource(
                    if (request.active) {
                        R.string.profile_delete_active_message
                    } else {
                        R.string.profile_delete_message
                    },
                ),
                color = c.textSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.key_delete),
                    color = c.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.key_cancel))
            }
        },
        containerColor = c.surface,
    )
}
