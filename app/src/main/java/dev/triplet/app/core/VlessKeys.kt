package dev.triplet.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class SubscriptionSelectionMode { MANUAL, AUTO }

data class VlessKey(
    val id: String,
    val name: String,
    val uri: String,
    val selectedNode: String? = null,
    val favoriteNodes: Set<String> = emptySet(),
    val subscriptionSelectionMode: SubscriptionSelectionMode = SubscriptionSelectionMode.MANUAL,
    /** Null disables scheduled refresh; a positive value is the requested interval in hours. */
    val subscriptionUpdateIntervalHours: Int? = null,
    /** Epoch millis of the last successfully prepared subscription cache. */
    val subscriptionUpdatedAt: Long? = null,
)

data class VlessKeys(val items: List<VlessKey>, val activeId: String?) {
    val active: VlessKey? get() = items.firstOrNull { it.id == activeId }

    fun delete(id: String): VlessKeys {
        if (items.none { it.id == id }) return this
        val remaining = items.filterNot { it.id == id }
        // Deleting the selected endpoint must not silently activate another one.
        // Keep inactive deletions stable, but require an explicit new selection
        // after the active key is removed.
        val next = if (activeId == id) null else activeId
        return VlessKeys(remaining, next)
    }

    fun toJson(): String = JSONObject().apply {
        put("activeId", activeId ?: JSONObject.NULL)
        put("items", JSONArray().apply {
            items.forEach { key ->
                put(JSONObject().apply {
                    put("id", key.id)
                    put("name", key.name)
                    put("uri", key.uri)
                    put("selectedNode", key.selectedNode ?: JSONObject.NULL)
                    put("favoriteNodes", JSONArray().apply {
                        key.favoriteNodes.sorted().forEach { nodeName -> put(nodeName) }
                    })
                    put("subscriptionSelectionMode", key.subscriptionSelectionMode.name)
                    put("subscriptionUpdateIntervalHours", key.subscriptionUpdateIntervalHours ?: JSONObject.NULL)
                    put("subscriptionUpdatedAt", key.subscriptionUpdatedAt ?: JSONObject.NULL)
                })
            }
        })
    }.toString()

    companion object {
        fun fromStored(json: String, legacyUri: String): VlessKeys {
            if (json.isBlank()) return legacyOrEmpty(legacyUri)
            return runCatching { fromJson(json) }.getOrElse {
                // Legacy fallback is migration-only. A present but damaged modern
                // snapshot must fail closed instead of resurrecting shadow state.
                VlessKeys(emptyList(), null)
            }
        }

        fun fromJson(json: String): VlessKeys {
            val root = try { JSONObject(json) } catch (e: Exception) {
                throw IllegalArgumentException("invalid VLESS key JSON", e)
            }
            val items = if (root.has("items")) root.getJSONArray("items") else JSONArray()
            val parsed = (0 until items.length()).map { i ->
                try {
                    val obj = items.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val uri = obj.getString("uri")
                    require(id.isNotBlank() && id == id.trim())
                    require(name.isNotBlank() && name == name.trim())
                    require(uri.isNotBlank() && VlessKeyParser.parse(uri) is ParseResult.Ok)
                    val selectedNode = if (!obj.has("selectedNode") || obj.isNull("selectedNode")) {
                        null
                    } else {
                        obj.getString("selectedNode").also(::validateNodeName)
                    }
                    val favoriteNodes = if (!obj.has("favoriteNodes") || obj.isNull("favoriteNodes")) {
                        emptySet()
                    } else {
                        val values = obj.getJSONArray("favoriteNodes")
                        require(values.length() <= MAX_FAVORITE_NODES) { "too many favorite nodes" }
                        buildSet {
                            for (index in 0 until values.length()) {
                                add(values.getString(index).also(::validateNodeName))
                            }
                        }
                    }
                    val selectionMode = if (!obj.has("subscriptionSelectionMode")) {
                        SubscriptionSelectionMode.MANUAL
                    } else {
                        val value = obj.getString("subscriptionSelectionMode")
                        SubscriptionSelectionMode.entries.firstOrNull { it.name == value }
                            ?: throw IllegalArgumentException("invalid subscription selection mode")
                    }
                    val subscriptionUpdateIntervalHours =
                        if (!obj.has("subscriptionUpdateIntervalHours") || obj.isNull("subscriptionUpdateIntervalHours")) {
                            null
                        } else {
                            obj.getInt("subscriptionUpdateIntervalHours").also(::validateUpdateIntervalHours)
                        }
                    val subscriptionUpdatedAt =
                        if (!obj.has("subscriptionUpdatedAt") || obj.isNull("subscriptionUpdatedAt")) {
                            null
                        } else {
                            obj.getLong("subscriptionUpdatedAt").also(::validateUpdatedAt)
                        }
                    VlessKey(
                        id = id,
                        name = name,
                        uri = uri,
                        selectedNode = selectedNode,
                        favoriteNodes = favoriteNodes,
                        subscriptionSelectionMode = selectionMode,
                        subscriptionUpdateIntervalHours = subscriptionUpdateIntervalHours,
                        subscriptionUpdatedAt = subscriptionUpdatedAt,
                    )
                } catch (e: IllegalArgumentException) {
                    throw e
                } catch (e: Exception) {
                    throw IllegalArgumentException("invalid VLESS key entry", e)
                }
            }
            require(parsed.map { it.id }.distinct().size == parsed.size) { "duplicate VLESS key id" }

            val hasActiveId = root.has("activeId")
            val activeValue = if (hasActiveId) root.get("activeId") else null
            require(activeValue == null || activeValue == JSONObject.NULL || activeValue is String)
            val activeId = when {
                !hasActiveId -> parsed.firstOrNull()?.id // compatibility with pre-activeId JSON
                activeValue == null || activeValue == JSONObject.NULL -> null
                activeValue is String -> {
                    require(parsed.any { it.id == activeValue }) { "unknown active VLESS key id" }
                    activeValue
                }
                else -> throw IllegalArgumentException("invalid active VLESS key id")
            }
            return VlessKeys(parsed, activeId)
        }

        private fun legacyOrEmpty(legacyUri: String): VlessKeys {
            if (legacyUri.isBlank()) return VlessKeys(emptyList(), null)
            val key = VlessKey(stableLegacyId(legacyUri), "VLESS", legacyUri)
            return VlessKeys(listOf(key), key.id)
        }

        private fun validateNodeName(value: String) {
            require(value.isNotBlank() && value == value.trim() && value.length <= 256)
            require(value.none { it.code < 0x20 || it.code == 0x7f })
        }

        private fun validateUpdateIntervalHours(value: Int) {
            require(value in MIN_UPDATE_INTERVAL_HOURS..MAX_UPDATE_INTERVAL_HOURS) {
                "invalid subscription update interval"
            }
        }

        private fun validateUpdatedAt(value: Long) {
            require(value > 0L) { "invalid subscription update timestamp" }
        }

        private fun stableLegacyId(uri: String): String =
            UUID.nameUUIDFromBytes(uri.toByteArray(StandardCharsets.UTF_8)).toString()

        const val MIN_UPDATE_INTERVAL_HOURS = 1
        const val MAX_UPDATE_INTERVAL_HOURS = 24 * 365
        private const val MAX_FAVORITE_NODES = 256
    }
}
