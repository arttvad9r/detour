package dev.detour.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.InflaterInputStream

sealed interface AmneziaVpnImportResult {
    data class XrayVless(val profile: VlessProfile) : AmneziaVpnImportResult
    data object Unsupported : AmneziaVpnImportResult
    data object Invalid : AmneziaVpnImportResult
}

/**
 * Decoder for Amnezia guest-access `vpn://` links.
 *
 * Amnezia serializes a JSON document, compresses it with Qt qCompress
 * (4-byte big-endian uncompressed length followed by a zlib stream), and
 * encodes the result as URL-safe Base64. Only credentials/endpoints are
 * imported; Detour remains the owner of routing, DNS and the Android TUN.
 */
object AmneziaVpnImporter {
    const val MAX_URI_CHARS = 128 * 1024
    private const val MAX_DECOMPRESSED_BYTES = 1024 * 1024
    private const val MAX_CONTAINERS = 32
    private const val MAX_XRAY_CONFIG_CHARS = 512 * 1024

    fun parse(raw: String): AmneziaVpnImportResult {
        val uri = raw.trim()
        if (!uri.startsWith("vpn://", ignoreCase = true) || uri.length > MAX_URI_CHARS) {
            return AmneziaVpnImportResult.Invalid
        }

        return try {
            val decoded = decodeQtCompressed(uri.substring(6))
                ?: return AmneziaVpnImportResult.Invalid
            val root = JSONObject(String(decoded, StandardCharsets.UTF_8))
            val containers = root.optJSONArray("containers")
                ?: return AmneziaVpnImportResult.Invalid
            if (containers.length() !in 1..MAX_CONTAINERS) return AmneziaVpnImportResult.Invalid

            val xrayContainers = containers.objects().filter {
                it.optString("container").equals("amnezia-xray", ignoreCase = true)
            }
            if (xrayContainers.isEmpty()) return AmneziaVpnImportResult.Unsupported
            if (xrayContainers.size != 1) return AmneziaVpnImportResult.Invalid

            parseXrayVless(root, xrayContainers.single())
        } catch (_: Exception) {
            AmneziaVpnImportResult.Invalid
        }
    }

    private fun parseXrayVless(root: JSONObject, container: JSONObject): AmneziaVpnImportResult {
        val xray = container.optJSONObject("xray") ?: return AmneziaVpnImportResult.Invalid
        val lastConfig = xray.optString("last_config")
            .takeIf { it.isNotBlank() && it.length <= MAX_XRAY_CONFIG_CHARS }
            ?: return AmneziaVpnImportResult.Invalid
        val config = try {
            JSONObject(lastConfig)
        } catch (_: Exception) {
            return AmneziaVpnImportResult.Invalid
        }

        val outbounds = config.optJSONArray("outbounds") ?: return AmneziaVpnImportResult.Invalid
        val vless = outbounds.objects().filter {
            it.optString("protocol").equals("vless", ignoreCase = true)
        }
        if (vless.isEmpty()) return AmneziaVpnImportResult.Unsupported
        if (vless.size != 1) return AmneziaVpnImportResult.Invalid

        val outbound = vless.single()
        val settings = outbound.optJSONObject("settings") ?: return AmneziaVpnImportResult.Invalid
        val vnext = settings.optJSONArray("vnext")?.singleObject()
            ?: return AmneziaVpnImportResult.Invalid
        val user = vnext.optJSONArray("users")?.singleObject()
            ?: return AmneziaVpnImportResult.Invalid
        val stream = outbound.optJSONObject("streamSettings") ?: return AmneziaVpnImportResult.Invalid
        val reality = stream.optJSONObject("realitySettings") ?: return AmneziaVpnImportResult.Invalid

        if (!stream.optString("network").equals("tcp", ignoreCase = true)) {
            return AmneziaVpnImportResult.Unsupported
        }
        if (!stream.optString("security").equals("reality", ignoreCase = true)) {
            return AmneziaVpnImportResult.Unsupported
        }
        val encryption = user.optString("encryption", "none")
        if (!encryption.equals("none", ignoreCase = true)) return AmneziaVpnImportResult.Unsupported

        val address = vnext.optString("address").trim()
        val port = vnext.optInt("port", -1)
        val uuid = user.optString("id").trim()
        val flow = user.optString("flow").trim()
        val fingerprint = reality.optString("fingerprint").trim()
        val publicKey = reality.optString("publicKey").trim()
        val serverName = reality.optString("serverName").trim()
        val shortId = reality.optString("shortId").trim()

        if (listOf(address, uuid, flow, fingerprint, publicKey, serverName).any { it.isBlank() }) {
            return AmneziaVpnImportResult.Invalid
        }
        if (port !in 1..65535) return AmneziaVpnImportResult.Invalid
        if (listOf(address, uuid, flow, fingerprint, publicKey, serverName, shortId).any(::hasControlCharacters)) {
            return AmneziaVpnImportResult.Invalid
        }

        val description = root.optString("description").trim()
            .takeIf { it.isNotBlank() && it.length <= 256 && !hasControlCharacters(it) }
            ?: address
        val host = if (address.contains(':')) "[$address]" else address
        val candidate = buildString {
            append("vless://")
            append(encode(uuid))
            append('@')
            append(host)
            append(':')
            append(port)
            append("?type=tcp&security=reality")
            append("&fp=").append(encode(fingerprint))
            append("&sni=").append(encode(serverName))
            append("&pbk=").append(encode(publicKey))
            append("&sid=").append(encode(shortId))
            append("&flow=").append(encode(flow))
            append('#').append(encode(description))
        }

        val parsed = VlessKeyParser.parse(candidate) as? ParseResult.Ok
            ?: return AmneziaVpnImportResult.Invalid
        val profile = parsed.profile
        if (!profile.server.equals(address, ignoreCase = true) || profile.port != port) {
            return AmneziaVpnImportResult.Invalid
        }
        return AmneziaVpnImportResult.XrayVless(profile)
    }

    private fun decodeQtCompressed(encoded: String): ByteArray? {
        if (encoded.isBlank()) return null
        val compressed = Base64.getUrlDecoder().decode(encoded)
        if (compressed.size <= 4) return null

        val declaredSize = ((compressed[0].toInt() and 0xff) shl 24) or
            ((compressed[1].toInt() and 0xff) shl 16) or
            ((compressed[2].toInt() and 0xff) shl 8) or
            (compressed[3].toInt() and 0xff)
        if (declaredSize !in 1..MAX_DECOMPRESSED_BYTES) return null

        val output = ByteArrayOutputStream(minOf(declaredSize, 16 * 1024))
        InflaterInputStream(ByteArrayInputStream(compressed, 4, compressed.size - 4)).use { input ->
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > declaredSize || total > MAX_DECOMPRESSED_BYTES) return null
                output.write(buffer, 0, count)
            }
            if (total != declaredSize) return null
        }
        return output.toByteArray()
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray.singleObject(): JSONObject? =
        if (length() == 1) optJSONObject(0) else null

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun hasControlCharacters(value: String): Boolean =
        value.any { it.code < 0x20 || it.code == 0x7f }
}
