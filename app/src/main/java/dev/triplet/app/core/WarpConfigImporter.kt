package dev.triplet.app.core

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

sealed interface WarpImportResult {
    data class Ok(val profile: WarpProfile) : WarpImportResult
    data object Invalid : WarpImportResult
    data object NoCompatibleProxies : WarpImportResult
}

/**
 * Imports only compatible WARP/AmneziaWG WireGuard proxies from a Clash/Mihomo YAML.
 * Routing rules, proxy groups, DNS policy, listeners and MASQUE entries are intentionally
 * ignored: Detour remains the owner of routing and only consumes the outbound credentials.
 */
object WarpConfigImporter {
    const val MAX_CHARS = 1024 * 1024
    private const val MAX_PROXIES = 128
    private const val MAX_ALIASES = 512

    fun parse(raw: String): WarpImportResult {
        if (raw.isBlank() || raw.length > MAX_CHARS) return WarpImportResult.Invalid
        val root = try {
            val options = LoaderOptions().apply {
                setAllowDuplicateKeys(false)
                setAllowRecursiveKeys(false)
                // Regular Warp Generator AWG files can reuse one shared anchor
                // for well over 100 endpoints. Keep the parser bounded, but do
                // not reject those valid files before we inspect their proxies.
                setMaxAliasesForCollections(MAX_ALIASES)
                setNestingDepthLimit(40)
                setCodePointLimit(MAX_CHARS)
                // Warp Generator configs rely heavily on `<<: *anchor` inheritance.
                setMergeOnCompose(true)
            }
            val loaded: Any? = Yaml(SafeConstructor(options)).load(raw)
            loaded as? Map<*, *>
        } catch (_: Exception) {
            null
        } ?: return WarpImportResult.Invalid

        val entries = root["proxies"] as? List<*> ?: return WarpImportResult.NoCompatibleProxies
        val proxies = entries.asSequence()
            .mapNotNull { it as? Map<*, *> }
            .filter { it.string("type").equals("wireguard", ignoreCase = true) }
            .filter { it["amnezia-wg-option"] is Map<*, *> }
            .mapNotNull(::parseProxy)
            .distinctBy { listOf(it.server, it.port, it.privateKey, it.amnezia.i1).joinToString("|") }
            .take(MAX_PROXIES)
            .toList()

        if (proxies.isEmpty()) return WarpImportResult.NoCompatibleProxies
        return WarpImportResult.Ok(WarpProfile.create(proxies = proxies))
    }

    private fun parseProxy(map: Map<*, *>): WarpProxy? = runCatching {
        val amz = map["amnezia-wg-option"] as Map<*, *>
        WarpProxy(
            name = map.string("name")?.takeIf { it.isNotBlank() } ?: "WARP",
            server = requireNotNull(map.string("server")),
            port = requireNotNull(map.int("port")),
            ip = requireNotNull(map.string("ip")),
            ipv6 = map.string("ipv6"),
            privateKey = requireNotNull(map.string("private-key")),
            publicKey = requireNotNull(map.string("public-key")),
            reserved = map.intList("reserved"),
            allowedIps = map.stringList("allowed-ips").ifEmpty { listOf("0.0.0.0/0") },
            udp = map.bool("udp") ?: true,
            mtu = map.int("mtu") ?: 1280,
            remoteDnsResolve = map.bool("remote-dns-resolve") ?: true,
            dns = map.stringList("dns"),
            amnezia = AmneziaWgOptions(
                jc = amz.int("jc"),
                jmin = amz.int("jmin"),
                jmax = amz.int("jmax"),
                s1 = amz.int("s1"),
                s2 = amz.int("s2"),
                h1 = amz.int("h1"),
                h2 = amz.int("h2"),
                h3 = amz.int("h3"),
                h4 = amz.int("h4"),
                i1 = amz.string("i1"),
                i2 = amz.string("i2"),
                i3 = amz.string("i3"),
                i4 = amz.string("i4"),
                i5 = amz.string("i5"),
            ),
        ).also(::validateWarpProxy)
    }.getOrNull()

    private fun Map<*, *>.string(key: String): String? =
        this[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun Map<*, *>.int(key: String): Int? = when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }

    private fun Map<*, *>.bool(key: String): Boolean? = when (val value = this[key]) {
        is Boolean -> value
        is String -> when (value.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
        else -> null
    }

    private fun Map<*, *>.stringList(key: String): List<String> =
        (this[key] as? List<*>)?.mapNotNull { value ->
            value?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }.orEmpty()

    private fun Map<*, *>.intList(key: String): List<Int> =
        (this[key] as? List<*>)?.mapNotNull {
            when (it) {
                is Number -> it.toInt()
                is String -> it.trim().toIntOrNull()
                else -> null
            }
        }.orEmpty()
}
