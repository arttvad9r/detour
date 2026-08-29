package dev.triplet.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
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
        put("activeId", activeId ?: JSONObject.NULL)
        put("items", JSONArray().apply {
            items.forEach { put(JSONObject().apply { put("id", it.id); put("name", it.name); put("uri", it.uri) }) }
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
                    VlessKey(id, name, uri)
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

        private fun stableLegacyId(uri: String): String =
            UUID.nameUUIDFromBytes(uri.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
