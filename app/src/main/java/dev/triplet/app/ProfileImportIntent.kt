package dev.triplet.app

import android.content.Intent
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal data class ProfileImportRequest(
    val value: String,
    val subscription: Boolean,
)

internal fun profileImportRequest(intent: Intent?): ProfileImportRequest? =
    intent?.let {
        profileImportRequest(
            action = it.action,
            mimeType = it.type,
            data = it.dataString,
            sharedText = it.getStringExtra(Intent.EXTRA_TEXT),
        )
    }

internal fun profileImportRequest(
    action: String?,
    mimeType: String?,
    data: String?,
    sharedText: String?,
): ProfileImportRequest? {
    val candidate = when (action) {
        Intent.ACTION_VIEW -> profileImportViewValue(data)
        Intent.ACTION_SEND -> sharedText.takeIf { mimeType == "text/plain" }
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

private fun profileImportViewValue(data: String?): String? {
    val value = data?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (value.startsWith("vless://", ignoreCase = true)) return value

    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (!uri.scheme.equals("detour", ignoreCase = true) || !uri.host.equals("import", ignoreCase = true)) {
        return null
    }
    return uri.rawQuery
        ?.split('&')
        ?.asSequence()
        ?.mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            if (pair.size != 2) null else pair[0] to pair[1]
        }
        ?.firstOrNull { (key, _) -> decodeQueryComponent(key) == "url" }
        ?.second
        ?.let(::decodeQueryComponent)
}

private fun decodeQueryComponent(value: String): String? = runCatching {
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}.getOrNull()

private const val MAX_PROFILE_IMPORT_CHARS = 8 * 1024
