package dev.triplet.app

import android.content.Intent
import android.net.Uri
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser

internal data class ProfileImportRequest(
    val value: String,
    val subscription: Boolean,
)

internal fun profileImportRequest(intent: Intent?): ProfileImportRequest? {
    if (intent == null) return null
    val candidate = when (intent.action) {
        Intent.ACTION_VIEW -> profileImportViewValue(intent.data)
        Intent.ACTION_SEND -> if (intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            null
        }
        else -> null
    } ?: return null

    val value = candidate.trim()
    if (
        value.isBlank() || value.length > MAX_PROFILE_IMPORT_CHARS ||
        value.any { it.code < 0x20 || it.code == 0x7f }
    ) return null

    val parsed = VlessKeyParser.parse(value) as? ParseResult.Ok ?: return null
    return ProfileImportRequest(
        value = value,
        subscription = parsed.profile.isSubscription,
    )
}

private fun profileImportViewValue(data: Uri?): String? {
    data ?: return null
    return when (data.scheme?.lowercase()) {
        "vless" -> data.toString()
        "detour" -> if (data.host.equals("import", ignoreCase = true)) {
            data.getQueryParameter("url")
        } else {
            null
        }
        else -> null
    }
}

private const val MAX_PROFILE_IMPORT_CHARS = 8 * 1024
