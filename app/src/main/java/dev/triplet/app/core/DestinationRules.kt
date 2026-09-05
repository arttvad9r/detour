package dev.triplet.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.IDN
import java.net.InetAddress

const val MAX_DESTINATION_RULES = 128
private const val MAX_DESTINATION_RULES_JSON_CHARS = 64 * 1024

enum class DestinationRuleType { DOMAIN, DOMAIN_SUFFIX, IP_CIDR }

data class DestinationRule(
    val type: DestinationRuleType,
    val value: String,
    val route: AppRoute,
)

object DestinationRules {
    fun create(
        type: DestinationRuleType,
        rawValue: String,
        route: AppRoute,
    ): DestinationRule? {
        val value = when (type) {
            DestinationRuleType.DOMAIN -> normalizeDomain(rawValue, suffix = false)
            DestinationRuleType.DOMAIN_SUFFIX -> normalizeDomain(rawValue, suffix = true)
            DestinationRuleType.IP_CIDR -> normalizeIpCidr(rawValue)
        } ?: return null
        return DestinationRule(type, value, route)
    }

    fun validate(rules: List<DestinationRule>) {
        require(rules.size <= MAX_DESTINATION_RULES) { "too many destination rules" }
        val seen = HashSet<Pair<DestinationRuleType, String>>()
        rules.forEach { rule ->
            val normalized = create(rule.type, rule.value, rule.route)
                ?: throw IllegalArgumentException("invalid destination rule")
            require(normalized.value == rule.value) { "destination rule is not normalized" }
            require(seen.add(rule.type to rule.value)) { "duplicate destination rule" }
        }
    }

    fun toJson(rules: List<DestinationRule>): String {
        validate(rules)
        return JSONArray().apply {
            rules.forEach { rule ->
                put(JSONObject().apply {
                    put("type", rule.type.name)
                    put("value", rule.value)
                    put("route", rule.route.name)
                })
            }
        }.toString()
    }

    fun fromStored(raw: String): List<DestinationRule> =
        runCatching { fromJsonStrict(raw) }.getOrDefault(emptyList())

    fun fromJsonStrict(raw: String): List<DestinationRule> {
        if (raw.isBlank()) return emptyList()
        require(raw.length <= MAX_DESTINATION_RULES_JSON_CHARS) { "destination rules are too large" }
        val json = JSONArray(raw)
        require(json.length() <= MAX_DESTINATION_RULES) { "too many destination rules" }
        val rules = buildList {
            repeat(json.length()) { index ->
                val item = json.getJSONObject(index)
                val type = DestinationRuleType.entries.firstOrNull {
                    it.name == item.getString("type")
                } ?: throw IllegalArgumentException("unknown destination rule type")
                val route = AppRoute.entries.firstOrNull {
                    it.name == item.getString("route")
                } ?: throw IllegalArgumentException("unknown destination route")
                val created = create(type, item.getString("value"), route)
                    ?: throw IllegalArgumentException("invalid destination rule")
                add(created)
            }
        }
        validate(rules)
        return rules
    }

    /**
     * Specific matches compile first so behavior does not depend on edit history:
     * exact domains, then longest domain suffixes, then narrowest IP CIDRs.
     */
    fun orderedForCompilation(rules: List<DestinationRule>): List<DestinationRule> {
        validate(rules)
        return rules.sortedWith(
            compareBy<DestinationRule> { typePriority(it.type) }
                .thenByDescending { specificity(it) }
                .thenBy { it.value },
        )
    }

    private fun typePriority(type: DestinationRuleType): Int = when (type) {
        DestinationRuleType.DOMAIN -> 0
        DestinationRuleType.DOMAIN_SUFFIX -> 1
        DestinationRuleType.IP_CIDR -> 2
    }

    private fun specificity(rule: DestinationRule): Int = when (rule.type) {
        DestinationRuleType.DOMAIN,
        DestinationRuleType.DOMAIN_SUFFIX,
        -> rule.value.length
        DestinationRuleType.IP_CIDR -> rule.value.substringAfterLast('/').toInt()
    }

    private fun normalizeDomain(raw: String, suffix: Boolean): String? {
        var value = raw.trim().trimEnd('.')
        if (suffix) value = value.removePrefix(".")
        if (value.isBlank() || value.length > 253) return null
        if (value.contains("://") || value.any { it == '/' || it == ',' || it.isWhitespace() }) return null
        return runCatching {
            IDN.toASCII(value.lowercase(), IDN.USE_STD3_ASCII_RULES)
                .lowercase()
                .trimEnd('.')
                .takeIf { ascii ->
                    ascii.isNotBlank() && ascii.length <= 253 &&
                        ascii.split('.').all { label -> label.isNotBlank() && label.length <= 63 }
                }
        }.getOrNull()
    }

    private fun normalizeIpCidr(raw: String): String? {
        val value = raw.trim()
        val slash = value.indexOf('/')
        if (slash <= 0 || slash != value.lastIndexOf('/') || slash == value.lastIndex) return null
        val address = value.substring(0, slash)
        val prefix = value.substring(slash + 1)
        return if (address.contains(':')) {
            normalizeIpv6Cidr(address, prefix)
        } else {
            normalizeIpv4Cidr(address, prefix)
        }
    }

    private fun normalizeIpv4Cidr(addressRaw: String, prefixRaw: String): String? {
        val prefix = prefixRaw.toIntOrNull()?.takeIf { it in 0..32 } ?: return null
        val octets = addressRaw.split('.')
        if (octets.size != 4) return null
        val values = octets.map { part ->
            if (part.isEmpty() || (part.length > 1 && part.startsWith('0'))) return null
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        }
        var address = 0
        values.forEach { octet -> address = (address shl 8) or octet }
        val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
        val network = address and mask
        val normalized = listOf(
            network ushr 24 and 0xff,
            network ushr 16 and 0xff,
            network ushr 8 and 0xff,
            network and 0xff,
        ).joinToString(".")
        return "$normalized/$prefix"
    }

    private fun normalizeIpv6Cidr(addressRaw: String, prefixRaw: String): String? {
        if ('%' in addressRaw || addressRaw.any { it.isWhitespace() }) return null
        val prefix = prefixRaw.toIntOrNull()?.takeIf { it in 0..128 } ?: return null
        val parsed = runCatching { InetAddress.getByName(addressRaw) }.getOrNull() ?: return null
        val bytes = parsed.address
        if (bytes.size != 16) return null

        val network = bytes.copyOf()
        var remaining = prefix
        for (index in network.indices) {
            when {
                remaining >= 8 -> remaining -= 8
                remaining <= 0 -> network[index] = 0
                else -> {
                    val mask = (0xff shl (8 - remaining)) and 0xff
                    network[index] = (network[index].toInt() and mask).toByte()
                    remaining = 0
                }
            }
        }
        return "${formatIpv6(network)}/$prefix"
    }

    private fun formatIpv6(bytes: ByteArray): String {
        val groups = IntArray(8) { index ->
            (bytes[index * 2].toInt() and 0xff shl 8) or
                (bytes[index * 2 + 1].toInt() and 0xff)
        }
        var bestStart = -1
        var bestLength = 0
        var index = 0
        while (index < groups.size) {
            if (groups[index] != 0) {
                index++
                continue
            }
            val start = index
            while (index < groups.size && groups[index] == 0) index++
            val length = index - start
            if (length >= 2 && length > bestLength) {
                bestStart = start
                bestLength = length
            }
        }

        val out = StringBuilder()
        index = 0
        while (index < groups.size) {
            if (index == bestStart) {
                out.append("::")
                index += bestLength
                continue
            }
            if (out.isNotEmpty() && out.last() != ':') out.append(':')
            out.append(groups[index].toString(16))
            index++
        }
        return out.toString()
    }
}
