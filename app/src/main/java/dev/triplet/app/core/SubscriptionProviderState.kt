package dev.triplet.app.core

import org.json.JSONObject

data class SubscriptionNode(
    val name: String,
    val type: String,
    val alive: Boolean,
    val delayMs: Int?,
)

data class SubscriptionUsage(
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val expireAtEpochSeconds: Long,
)

data class SubscriptionProviderState(
    val available: Boolean,
    val totalNodes: Int,
    val aliveNodes: Int,
    val nodes: List<SubscriptionNode>,
    val updatedAt: String?,
    val usage: SubscriptionUsage?,
) {
    companion object {
        val Unavailable = SubscriptionProviderState(
            available = false,
            totalNodes = 0,
            aliveNodes = 0,
            nodes = emptyList(),
            updatedAt = null,
            usage = null,
        )
    }
}

object SubscriptionProviderStateParser {
    private const val PROVIDER_NAME = "DETOUR_SUBSCRIPTION"
    private const val MAX_VISIBLE_NODES = 256
    private const val MAX_NODE_NAME_CHARS = 256
    private const val MAX_NODE_TYPE_CHARS = 64
    private const val MAX_PROVIDER_JSON_CHARS = 5 * 1024 * 1024

    fun parse(raw: String): SubscriptionProviderState {
        if (raw.isBlank() || raw.length > MAX_PROVIDER_JSON_CHARS) {
            return SubscriptionProviderState.Unavailable
        }
        return runCatching {
            val root = JSONObject(raw)
            require(root.optString("name") == PROVIDER_NAME)
            val proxies = root.optJSONArray("proxies") ?: return@runCatching SubscriptionProviderState.Unavailable
            var aliveCount = 0
            val nodes = buildList {
                for (index in 0 until proxies.length()) {
                    val proxy = proxies.optJSONObject(index) ?: continue
                    // Summary counters describe the provider itself, even when an
                    // unsafe remote label is intentionally omitted from the UI list.
                    val alive = proxy.optBoolean("alive", false)
                    if (alive) aliveCount++
                    val name = safeRemoteLabel(proxy.optString("name"), MAX_NODE_NAME_CHARS) ?: continue
                    val type = safeRemoteLabel(proxy.optString("type"), MAX_NODE_TYPE_CHARS) ?: "unknown"
                    if (size >= MAX_VISIBLE_NODES) continue
                    val history = proxy.optJSONArray("history")
                    val lastDelay = history
                        ?.takeIf { it.length() > 0 }
                        ?.optJSONObject(history.length() - 1)
                        ?.optInt("delay", 0)
                        ?.takeIf { it > 0 }
                    add(
                        SubscriptionNode(
                            name = name,
                            type = type,
                            alive = alive,
                            delayMs = lastDelay,
                        ),
                    )
                }
            }
            val usage = root.optJSONObject("subscriptionInfo")?.let { info ->
                SubscriptionUsage(
                    uploadBytes = info.optLong("Upload", info.optLong("upload", 0L)).coerceAtLeast(0L),
                    downloadBytes = info.optLong("Download", info.optLong("download", 0L)).coerceAtLeast(0L),
                    totalBytes = info.optLong("Total", info.optLong("total", 0L)).coerceAtLeast(0L),
                    expireAtEpochSeconds = info.optLong("Expire", info.optLong("expire", 0L)).coerceAtLeast(0L),
                )
            }
            SubscriptionProviderState(
                available = true,
                totalNodes = proxies.length(),
                aliveNodes = aliveCount,
                nodes = nodes,
                updatedAt = safeRemoteLabel(root.optString("updatedAt"), 128),
                usage = usage,
            )
        }.getOrDefault(SubscriptionProviderState.Unavailable)
    }

    private fun safeRemoteLabel(value: String, maxChars: Int): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.length > maxChars) return null
        if (trimmed.any { it.code < 0x20 || it.code == 0x7f }) return null
        return trimmed
    }
}
