package dev.triplet.app.core

import java.net.URI
import java.net.URLDecoder

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

    fun parse(uriRaw: String): ParseResult {
        val uri = uriRaw.trim()
        if (!uri.startsWith("vless://")) return ParseResult.Err(ERR_FORMAT)
        return try {
            val u = URI(uri)
            val userInfo = u.userInfo ?: return ParseResult.Err(ERR_FORMAT)
            val host = u.host ?: return ParseResult.Err(ERR_FORMAT)
            val port = if (u.port > 0) u.port else 443
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
            val pbk = q["pbk"] ?: return ParseResult.Err(ERR_SECURITY)
            val profile = VlessProfile(
                uuid = userInfo, server = host, port = port,
                sni = q["sni"] ?: host,
                publicKey = pbk, shortId = q["sid"] ?: "",
                fingerprint = q["fp"] ?: "chrome",
                flow = q["flow"] ?: "xtls-rprx-vision",
                name = URLDecoder.decode(u.fragment ?: "VLESS", "UTF-8"),
            )
            ParseResult.Ok(profile)
        } catch (e: Exception) {
            ParseResult.Err(ERR_FORMAT)
        }
    }
}
