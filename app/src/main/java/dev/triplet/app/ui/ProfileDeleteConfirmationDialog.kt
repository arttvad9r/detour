package dev.triplet.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        title = {
            Text(
                text = stringResource(R.string.profile_delete_title),
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
                    color = c.textSecondary,
                )
                if (request.failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.profile_delete_error),
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
                Text(stringResource(R.string.key_cancel))
            }
        },
        containerColor = c.surface,
    )
}
