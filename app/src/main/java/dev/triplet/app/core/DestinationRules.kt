package dev.triplet.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.IDN

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
            DestinationRuleType.IP_CIDR -> normalizeIpv4Cidr(rawValue)
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
     * exact domains, then longest domain suffixes, then narrowest IPv4 CIDRs.
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
        DestinationRuleType.IP_CIDR -> rule.value.substringAfter('/').toInt()
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

    private fun normalizeIpv4Cidr(raw: String): String? {
        val parts = raw.trim().split('/')
        if (parts.size != 2) return null
        val prefix = parts[1].toIntOrNull()?.takeIf { it in 0..32 } ?: return null
        val octets = parts[0].split('.')
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
}
