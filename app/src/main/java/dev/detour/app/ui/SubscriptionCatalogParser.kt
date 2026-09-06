package dev.detour.app.ui

import org.json.JSONObject

private val supportedSubscriptionCatalogTypes = setOf(
    "vless",
    "vmess",
    "trojan",
    "ss",
    "ssr",
    "hysteria",
    "hysteria2",
    "tuic",
    "anytls",
    "mieru",
)

internal fun canonicalSubscriptionCatalogType(value: String): String? =
    value.trim().lowercase().takeIf { it in supportedSubscriptionCatalogTypes }

internal fun parseSubscriptionCatalog(
    raw: String,
    maxJsonChars: Int,
    maxNodes: Int,
): List<SubscriptionCatalogNode> {
    if (raw.isBlank() || raw.length > maxJsonChars || maxNodes <= 0) return emptyList()
    return runCatching {
        val nodes = JSONObject(raw).optJSONArray("nodes") ?: return@runCatching emptyList()
        buildList {
            for (index in 0 until minOf(nodes.length(), maxNodes)) {
                val node = nodes.optJSONObject(index) ?: continue
                val name = safeSubscriptionCatalogLabel(node.optString("name"), 256) ?: continue
                val type = canonicalSubscriptionCatalogType(node.optString("type")) ?: continue
                add(SubscriptionCatalogNode(name, type))
            }
        }.distinctBy { it.name }
    }.getOrDefault(emptyList())
}

private fun safeSubscriptionCatalogLabel(value: String, maxChars: Int): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank() || trimmed.length > maxChars) return null
    if (trimmed.any { it.code < 0x20 || it.code == 0x7f }) return null
    return trimmed
}
