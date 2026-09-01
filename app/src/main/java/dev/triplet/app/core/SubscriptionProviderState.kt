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

    fun parse(raw: String): SubscriptionProviderState {
        if (raw.isBlank()) return SubscriptionProviderState.Unavailable
        return runCatching {
            val root = JSONObject(raw)
            require(root.optString("name") == PROVIDER_NAME)
            val proxies = root.optJSONArray("proxies") ?: return@runCatching SubscriptionProviderState.Unavailable
            var aliveCount = 0
            val nodes = buildList {
                for (index in 0 until proxies.length()) {
                    val proxy = proxies.optJSONObject(index) ?: continue
                    val name = proxy.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val alive = proxy.optBoolean("alive", false)
                    if (alive) aliveCount++
                    if (size >= MAX_VISIBLE_NODES) continue
                    val history = proxy.optJSONArray("history")
                    val lastDelay = history
                        ?.optJSONObject((history.length() - 1).coerceAtLeast(0))
                        ?.optInt("delay", 0)
                        ?.takeIf { it > 0 }
                    add(
                        SubscriptionNode(
                            name = name,
                            type = proxy.optString("type").ifBlank { "unknown" },
                            alive = alive,
                            delayMs = lastDelay,
                        ),
                    )
                }
            }
            val usage = root.optJSONObject("subscriptionInfo")?.let { info ->
                SubscriptionUsage(
                    uploadBytes = info.optLong("Upload", info.optLong("upload", 0L)),
                    downloadBytes = info.optLong("Download", info.optLong("download", 0L)),
                    totalBytes = info.optLong("Total", info.optLong("total", 0L)),
                    expireAtEpochSeconds = info.optLong("Expire", info.optLong("expire", 0L)),
                )
            }
            SubscriptionProviderState(
                available = true,
                totalNodes = proxies.length(),
                aliveNodes = aliveCount,
                nodes = nodes,
                updatedAt = root.optString("updatedAt").takeIf { it.isNotBlank() },
                usage = usage,
            )
        }.getOrDefault(SubscriptionProviderState.Unavailable)
    }
}
