package dev.triplet.app.core

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

sealed interface WarpImportResult {
    data class Ok(val profile: WarpProfile) : WarpImportResult
    data object Invalid : WarpImportResult
    data object NoCompatibleProxies : WarpImportResult
}

object WarpConfigImporter {
    const val MAX_CHARS = 1024 * 1024
    private const val MAX_PROXIES = 128
    private const val MAX_ALIASES = 512

    private val nativeInterfaceHeader = Regex("""(?im)^\s*\[Interface]\s*$""")
    private val amneziaKeys = setOf(
        "jc", "jmin", "jmax", "s1", "s2", "h1", "h2", "h3", "h4",
        "i1", "i2", "i3", "i4", "i5",
    )

    fun parse(raw: String): WarpImportResult {
        if (raw.isBlank() || raw.length > MAX_CHARS) return WarpImportResult.Invalid
        val normalized = raw.removePrefix("\uFEFF")
        return if (nativeInterfaceHeader.containsMatchIn(normalized)) {
            parseNativeConf(normalized)
        } else {
            parseMihomoYaml(normalized)
        }
    }

    private fun parseMihomoYaml(raw: String): WarpImportResult {
        val root = try {
            val options = LoaderOptions().apply {
                setAllowDuplicateKeys(false)
                setAllowRecursiveKeys(false)
                setMaxAliasesForCollections(MAX_ALIASES)
                setNestingDepthLimit(40)
                setCodePointLimit(MAX_CHARS)
                setMergeOnCompose(true)
            }
            val loaded: Any? = Yaml(SafeConstructor(options)).load(raw)
            loaded as? Map<*, *>
        } catch (_: Exception) {
            null
        } ?: return WarpImportResult.Invalid

        val entries = root["proxies"] as? List<*> ?: return WarpImportResult.NoCompatibleProxies
        val parsed = entries.asSequence()
            .mapNotNull { it as? Map<*, *> }
            .filter { it.string("type").equals("wireguard", ignoreCase = true) }
            .filter { it["amnezia-wg-option"] is Map<*, *> }
            .mapNotNull(::parseYamlProxy)
            .distinctBy { listOf(it.server, it.port, it.privateKey, it.amnezia.i1).joinToString("|") }
            .toList()

        if (parsed.isEmpty()) return WarpImportResult.NoCompatibleProxies

        // Warp Generator marks its direct/recommended Cloudflare endpoints with ⭐.
        // Prefer that subset when it exists instead of mixing it with geo relay nodes.
        // For generic Clash/AWG files without this convention, preserve all entries.
        val recommended = parsed.filter { it.name.contains("⭐") }
        val proxies = (recommended.ifEmpty { parsed }).take(MAX_PROXIES)
        return WarpImportResult.Ok(WarpProfile.create(proxies = proxies))
    }

    private fun parseNativeConf(raw: String): WarpImportResult {
        val sections = parseIni(raw)
        val iface = sections.firstOrNull { it.name.equals("Interface", ignoreCase = true) }?.values
            ?: return WarpImportResult.NoCompatibleProxies
        val peers = sections.filter { it.name.equals("Peer", ignoreCase = true) }
        if (peers.isEmpty()) return WarpImportResult.NoCompatibleProxies
        if (iface.keys.none { it in amneziaKeys }) return WarpImportResult.NoCompatibleProxies

        val privateKey = iface["privatekey"]?.takeIf { it.isNotBlank() }
            ?: return WarpImportResult.NoCompatibleProxies
        val addresses = csv(iface["address"]).map(::withoutCidr)
        val ip = addresses.firstOrNull { it.isNotBlank() && !it.contains(':') }
            ?: return WarpImportResult.NoCompatibleProxies
        val ipv6 = addresses.firstOrNull { it.contains(':') }
        val dns = csv(iface["dns"])
        val mtu = iface["mtu"]?.toIntOrNull() ?: 1280
        val reserved = csv(iface["reserved"]).mapNotNull { it.toIntOrNull() }
        val amnezia = AmneziaWgOptions(
            jc = iface.iniInt("jc"),
            jmin = iface.iniInt("jmin"),
            jmax = iface.iniInt("jmax"),
            s1 = iface.iniInt("s1"),
            s2 = iface.iniInt("s2"),
            h1 = iface.iniInt("h1"),
            h2 = iface.iniInt("h2"),
            h3 = iface.iniInt("h3"),
            h4 = iface.iniInt("h4"),
            i1 = iface.iniValue("i1"),
            i2 = iface.iniValue("i2"),
            i3 = iface.iniValue("i3"),
            i4 = iface.iniValue("i4"),
            i5 = iface.iniValue("i5"),
        )

        val proxies = peers.asSequence().mapNotNull { section ->
            runCatching {
                val peer = section.values
                val publicKey = requireNotNull(peer["publickey"]?.takeIf { it.isNotBlank() })
                val endpoint = requireNotNull(parseEndpoint(peer["endpoint"] ?: ""))
                val allowedIps = csv(peer["allowedips"]).ifEmpty { listOf("0.0.0.0/0") }
                WarpProxy(
                    name = "WARP ${endpoint.first}:${endpoint.second}",
                    server = endpoint.first,
                    port = endpoint.second,
                    ip = ip,
                    ipv6 = ipv6,
                    privateKey = privateKey,
                    publicKey = publicKey,
                    reserved = reserved,
                    allowedIps = allowedIps,
                    udp = true,
                    mtu = mtu,
                    persistentKeepalive = peer["persistentkeepalive"]?.toIntOrNull()
                        ?: iface["persistentkeepalive"]?.toIntOrNull(),
                    remoteDnsResolve = true,
                    dns = dns,
                    amnezia = amnezia,
                ).also(::validateWarpProxy)
            }.getOrNull()
        }
            .distinctBy { listOf(it.server, it.port, it.privateKey, it.amnezia.i1).joinToString("|") }
            .take(MAX_PROXIES)
            .toList()

        if (proxies.isEmpty()) return WarpImportResult.NoCompatibleProxies
        return WarpImportResult.Ok(WarpProfile.create(name = "WARP / AmneziaWG", proxies = proxies))
    }

    private fun parseYamlProxy(map: Map<*, *>): WarpProxy? = runCatching {
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
            persistentKeepalive = map.int("persistent-keepalive"),
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

    private data class IniSection(val name: String, val values: Map<String, String>)

    private fun parseIni(raw: String): List<IniSection> {
        val result = mutableListOf<IniSection>()
        var name: String? = null
        var values = linkedMapOf<String, String>()

        fun flush() {
            val current = name ?: return
            result += IniSection(current, values.toMap())
        }

        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEach
            if (line.startsWith("[") && line.endsWith("]") && line.length > 2) {
                flush()
                name = line.substring(1, line.length - 1).trim()
                values = linkedMapOf()
                return@forEach
            }
            if (name == null) return@forEach
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val key = line.substring(0, eq).trim().lowercase()
            val value = line.substring(eq + 1).trim()
            if (key.isNotEmpty()) values[key] = value
        }
        flush()
        return result
    }

    private fun parseEndpoint(raw: String): Pair<String, Int>? {
        val value = raw.trim()
        if (value.startsWith("[")) {
            val close = value.indexOf(']')
            if (close <= 1 || close + 1 >= value.length || value[close + 1] != ':') return null
            val server = value.substring(1, close).trim()
            val port = value.substring(close + 2).trim().toIntOrNull() ?: return null
            return if (server.isNotEmpty() && port in 1..65535) server to port else null
        }
        val colon = value.lastIndexOf(':')
        if (colon <= 0) return null
        val server = value.substring(0, colon).trim()
        val port = value.substring(colon + 1).trim().toIntOrNull() ?: return null
        return if (server.isNotEmpty() && port in 1..65535) server to port else null
    }

    private fun csv(value: String?): List<String> =
        value?.split(',')?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.orEmpty()

    private fun withoutCidr(value: String): String = value.substringBefore('/').trim()

    private fun Map<String, String>.iniInt(key: String): Int? = this[key]?.toIntOrNull()
    private fun Map<String, String>.iniValue(key: String): String? = this[key]?.takeIf { it.isNotBlank() }

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
