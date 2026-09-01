package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.triplet.app.R

@Composable
fun ProfileDeleteConfirmationDialog(
    request: ProfileDeleteRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
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
            DetourCard(
                Modifier.widthIn(max = 420.dp),
            ) {
                Column(Modifier.padding(Spacing.space20)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(c.errorSoft, AppShapes.extraSmall)
                                .border(
                                    1.dp,
                                    c.error.copy(alpha = 0.34f),
                                    AppShapes.extraSmall,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                tint = c.error,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.profile_delete_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = c.textPrimary,
                            modifier = Modifier.padding(start = Spacing.space12),
                        )
                    }

                    Spacer(Modifier.height(Spacing.space16))
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
                        Spacer(Modifier.height(Spacing.space12))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(c.errorSoft, AppShapes.small)
                                .border(
                                    1.dp,
                                    c.error.copy(alpha = 0.30f),
                                    AppShapes.small,
                                )
                                .padding(
                                    horizontal = Spacing.space12,
                                    vertical = Spacing.space12,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_warning),
                                contentDescription = null,
                                tint = c.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.profile_delete_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textPrimary,
                                modifier = Modifier
                                    .padding(start = Spacing.space8)
                                    .weight(1f),
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.space20))
                    DetourButton(
                        text = stringResource(R.string.key_cancel),
                        onClick = onDismiss,
                        style = ButtonStyle.SECONDARY,
                    )
                    Spacer(Modifier.height(Spacing.space8))
                    DetourButton(
                        text = stringResource(
                            if (request.failed) R.string.action_retry else R.string.key_delete,
                        ),
                        onClick = onConfirm,
                        container = c.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        disabledContainer = c.errorSoft,
                        disabledContent = c.error,
                        borderColor = c.error.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}
