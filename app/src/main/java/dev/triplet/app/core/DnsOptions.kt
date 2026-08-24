package dev.triplet.app.core

/** Выбор DNS-резолвера для движка. */
object DnsOptions {
    val servers = linkedMapOf(
        "google" to "8.8.8.8",
        "cloudflare" to "https://1.1.1.1/dns-query",
        "adguard" to "https://dns.adguard-dns.io/dns-query",
    )
    const val CUSTOM = "custom"
    const val DEFAULT_SERVER = "8.8.8.8"

    fun resolve(id: String, custom: String): String = when {
        id == CUSTOM && custom.isNotBlank() -> custom.trim()
        servers.containsKey(id) -> servers.getValue(id)
        else -> DEFAULT_SERVER
    }
}
