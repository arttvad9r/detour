package dev.triplet.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class VlessKey(val id: String, val name: String, val uri: String)

data class VlessKeys(val items: List<VlessKey>, val activeId: String?) {
    val active: VlessKey? get() = items.firstOrNull { it.id == activeId }

    fun delete(id: String): VlessKeys {
        if (items.none { it.id == id }) return this
        val remaining = items.filterNot { it.id == id }
        val next = when {
            remaining.isEmpty() -> null
            activeId != id -> activeId
            else -> remaining.first().id
        }
        return VlessKeys(remaining, next)
    }

    fun toJson(): String = JSONObject().apply {
        put("activeId", activeId)
        put("items", JSONArray().apply {
            items.forEach { put(JSONObject().apply { put("id", it.id); put("name", it.name); put("uri", it.uri) }) }
        })
    }.toString()

    companion object {
        fun fromStored(json: String, legacyUri: String): VlessKeys {
            if (json.isBlank()) {
                if (legacyUri.isBlank()) return VlessKeys(emptyList(), null)
                val key = VlessKey(UUID.randomUUID().toString(), "VLESS", legacyUri)
                return VlessKeys(listOf(key), key.id)
            }
            return runCatching { fromJson(json) }.getOrElse {
                if (legacyUri.isBlank()) VlessKeys(emptyList(), null)
                else {
                    val key = VlessKey(UUID.randomUUID().toString(), "VLESS", legacyUri)
                    VlessKeys(listOf(key), key.id)
                }
            }
        }

        fun fromJson(json: String): VlessKeys {
            val root = JSONObject(json)
            val items = root.optJSONArray("items") ?: JSONArray()
            val parsed = (0 until items.length()).mapNotNull { i ->
                items.optJSONObject(i)?.let { obj ->
                    val uri = obj.optString("uri")
                    if (uri.isBlank()) null else VlessKey(
                        obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        obj.optString("name").ifBlank { "VLESS" },
                        uri,
                    )
                }
            }
            require(parsed.map { it.id }.distinct().size == parsed.size) { "duplicate VLESS key id" }
            return VlessKeys(parsed, root.optString("activeId").takeIf { id -> parsed.any { it.id == id } } ?: parsed.firstOrNull()?.id)
        }
    }
}
