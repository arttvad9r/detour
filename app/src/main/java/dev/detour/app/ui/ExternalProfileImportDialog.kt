package dev.detour.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.detour.app.ProfileImportRequest
import dev.detour.app.R
import dev.detour.app.core.ParseResult
import dev.detour.app.core.VlessKeyParser

@Composable
internal fun ExternalProfileImportDialog(
    request: ProfileImportRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val profile = (VlessKeyParser.parse(request.value) as? ParseResult.Ok)?.profile ?: return
    val kind = stringResource(
        if (request.subscription) R.string.subscription_profile_section else R.string.protocol_vless,
    )
    val endpoint = if (request.subscription) {
        profile.server
    } else {
        "${profile.server}:${profile.port}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_add_title)) },
        text = {
            Text(
                stringResource(
                    R.string.external_profile_import_summary,
                    kind,
                    endpoint,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.external_profile_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.key_cancel))
            }
        },
    )
}
