package dev.triplet.app.core

import java.net.URI
import java.net.URLDecoder
import java.util.UUID

data class VlessProfile(
    val uuid: String,
    val server: String,
    val port: Int,
    val sni: String,
    val publicKey: String,
    val shortId: String,
    val fingerprint: String,
    val flow: String,
    val name: String,
)

sealed interface ParseResult {
    data class Ok(val profile: VlessProfile) : ParseResult
    data class Err(val reasonResId: Int) : ParseResult
}

object VlessKeyParser {

    // R.string.* placeholders resolved at call sites; constants keep tests stable.
    const val ERR_FORMAT = 1       // err_invalid_key
    const val ERR_TRANSPORT = 2    // err_unsupported_transport
    const val ERR_SECURITY = 3     // err_reality_required

    private val fingerprints = setOf(
        "chrome", "firefox", "safari", "iOS", "android", "edge", "360", "qq", "random",
    )
    private const val FLOW = "xtls-rprx-vision"

    fun parse(uriRaw: String): ParseResult {
        val uri = uriRaw.trim()
        if (!uri.startsWith("vless://")) return ParseResult.Err(ERR_FORMAT)
        return try {
            val u = URI(uri)
            val userInfo = u.userInfo ?: return ParseResult.Err(ERR_FORMAT)
            UUID.fromString(userInfo)
            val host = u.host?.takeIf { it.isNotBlank() } ?: return ParseResult.Err(ERR_FORMAT)
            if (host.any { it.code < 0x20 || it.code == 0x7f }) return ParseResult.Err(ERR_FORMAT)
            val port = if (u.port == -1) 443 else u.port
            if (port !in 1..65535) return ParseResult.Err(ERR_FORMAT)
            val q = mutableMapOf<String, String>().apply {
                u.rawQuery?.split('&')?.forEach { p ->
                    val i = p.indexOf('=')
                    if (i > 0) put(URLDecoder.decode(p.substring(0, i), "UTF-8"),
                                   URLDecoder.decode(p.substring(i + 1), "UTF-8"))
                }
            }
            if (q["type"] != null && q["type"] != "tcp") return ParseResult.Err(ERR_TRANSPORT)
            // Поддерживаемый профиль: Reality (+vision). Plain TLS сознательно отклонён:
            // продукт заточен под проверенный профиль пользователя (см. спеку).
            if (q["security"] != "reality") return ParseResult.Err(ERR_SECURITY)
            val pbk = q["pbk"]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{32,128}")) }
                ?: return ParseResult.Err(ERR_SECURITY)
            val sid = q["sid"]?.takeIf { it.matches(Regex("[0-9a-fA-F]{1,16}")) }
                ?: return ParseResult.Err(ERR_SECURITY)
            // Current mihomo spells this fingerprint "iOS". Accept the older
            // lowercase form from existing links but normalize the generated YAML.
            val fp = (q["fp"] ?: "chrome").let { if (it == "ios") "iOS" else it }
            if (fp !in fingerprints) return ParseResult.Err(ERR_FORMAT)
            val flow = q["flow"] ?: FLOW
            if (flow != FLOW) return ParseResult.Err(ERR_FORMAT)
            val values = listOf(userInfo, host, pbk, sid, fp, flow, q["sni"] ?: host, u.fragment ?: "")
            if (values.any { it.any { c -> c.code < 0x20 || c.code == 0x7f } }) return ParseResult.Err(ERR_FORMAT)
            val profile = VlessProfile(
                uuid = userInfo, server = host, port = port,
                sni = q["sni"]?.takeIf { it.isNotBlank() } ?: host,
                publicKey = pbk, shortId = sid,
                fingerprint = fp,
                flow = flow,
                name = URLDecoder.decode(u.fragment ?: "VLESS", "UTF-8"),
            )
            ParseResult.Ok(profile)
        } catch (e: Exception) {
            ParseResult.Err(ERR_FORMAT)
        }
    }
}
