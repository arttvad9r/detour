package dev.triplet.app.core

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
        return try {
            if (candidate.startsWith("https://")) URI(candidate).let { it.scheme == "https" && !it.host.isNullOrBlank() }
            else InetAddress.getByName(candidate).let { it.hostAddress == candidate || candidate.contains(':') }
        } catch (_: Exception) { false }
    }

    fun resolve(id: String, custom: String): String = when {
        id == CUSTOM && isValid(custom) -> custom.trim()
        servers.containsKey(id) -> servers.getValue(id)
        else -> DEFAULT_SERVER
    }

    fun isSelectionValid(id: String, custom: String): Boolean =
        id != CUSTOM || isValid(custom)
}
