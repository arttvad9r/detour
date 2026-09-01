package dev.triplet.app.core

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/** Выбор DNS-резолвера для движка. */
object DnsOptions {
    val servers = linkedMapOf(
        "google" to "8.8.8.8",
        "cloudflare" to "https://1.1.1.1/dns-query",
        "adguard" to "https://dns.adguard-dns.io/dns-query",
    )
    const val CUSTOM = "custom"
    const val DEFAULT_SERVER = "8.8.8.8"

    fun isValid(value: String): Boolean {
        val candidate = value.trim()
        if (candidate.isEmpty() || candidate.any { it.code < 0x20 || it.code == 0x7f }) return false
        if (isIpLiteral(candidate)) return true
        return runCatching {
            val uri = URI(candidate)
            uri.scheme == "https" &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                (uri.port == -1 || uri.port in 1..65535)
        }.getOrDefault(false)
    }

    fun resolve(id: String, custom: String): String {
        // An absent selection predates the DNS picker and means the historical
        // default. A nonblank unknown/corrupt id must not silently change the
        // user's resolver to Google.
        val selection = id.ifBlank { "google" }
        require(isSelectionValid(selection, custom)) { "invalid DNS selection" }
        return if (selection == CUSTOM) custom.trim() else servers.getValue(selection)
    }

    fun isSelectionValid(id: String, custom: String): Boolean =
        id in servers || (id == CUSTOM && isValid(custom))

    /**
     * Hostname-based DoH needs a bootstrap resolver before mihomo can contact it.
     * IP-literal resolvers do not need an extra lookup.
     */
    fun bootstrapServer(value: String): String? {
        val candidate = value.trim()
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val host = uri.host?.removePrefix("[")?.removeSuffix("]") ?: return null
        return if (uri.scheme == "https" && !isIpLiteral(host)) DEFAULT_SERVER else null
    }

    internal fun isIpLiteral(value: String): Boolean {
        val candidate = value.removePrefix("[").removeSuffix("]")
        return isIpv4Literal(candidate) || isIpv6Literal(candidate)
    }

    private fun isIpv4Literal(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() && part.length <= 3 &&
                part.all(Char::isDigit) &&
                part.toIntOrNull() in 0..255
        }
    }

    private fun isIpv6Literal(value: String): Boolean {
        if (!value.contains(':')) return false
        // Restrict the input before using InetAddress so validation never turns a
        // hostname into a blocking DNS lookup on the Compose/UI thread.
        if (value.any { it !in "0123456789abcdefABCDEF:." }) return false
        return runCatching { InetAddress.getByName(value) is Inet6Address }.getOrDefault(false)
    }
}
