package dev.detour.app.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Collection of WireGuard-family profiles (Cloudflare WARP and AmneziaWG).
 *
 * The persisted DataStore key historically contained a single [WarpProfile].
 * [fromStored] deliberately accepts both that legacy shape and the collection
 * shape so upgrades require no destructive migration.
 */
data class WireGuardProfiles(
    val items: List<WarpProfile>,
    val activeId: String?,
) {
    val active: WarpProfile? get() = items.firstOrNull { it.id == activeId }

    init {
        require(items.map { it.id }.distinct().size == items.size) { "duplicate WireGuard profile id" }
        require(activeId == null || items.any { it.id == activeId }) { "unknown active WireGuard profile id" }
    }

    fun delete(id: String): WireGuardProfiles {
        if (items.none { it.id == id }) return this
        val remaining = items.filterNot { it.id == id }
        return WireGuardProfiles(remaining, if (activeId == id) null else activeId)
    }

    fun toJson(): String = JSONObject().apply {
        put("activeId", activeId ?: JSONObject.NULL)
        put("items", JSONArray().apply {
            items.forEach { put(JSONObject(it.toJson())) }
        })
    }.toString()

    companion object {
        fun empty(): WireGuardProfiles = WireGuardProfiles(emptyList(), null)

        fun fromLegacy(profile: WarpProfile?): WireGuardProfiles =
            if (profile == null) empty() else WireGuardProfiles(listOf(profile), profile.id)

        fun fromStored(json: String): WireGuardProfiles {
            if (json.isBlank()) return empty()
            return runCatching {
                val root = JSONObject(json)
                if (root.has("items")) fromJson(json)
                else WarpProfile.fromJson(json).let(::fromLegacy)
            }.getOrElse { empty() }
        }

        fun fromJson(json: String): WireGuardProfiles {
            val root = try { JSONObject(json) } catch (e: Exception) {
                throw IllegalArgumentException("invalid WireGuard profile collection JSON", e)
            }
            val array = root.getJSONArray("items")
            val parsed = (0 until array.length()).map { index ->
                WarpProfile.fromJson(array.getJSONObject(index).toString())
            }
            require(parsed.map { it.id }.distinct().size == parsed.size) { "duplicate WireGuard profile id" }

            val active = when {
                !root.has("activeId") || root.isNull("activeId") -> null
                else -> root.getString("activeId").also { id ->
                    require(parsed.any { it.id == id }) { "unknown active WireGuard profile id" }
                }
            }
            return WireGuardProfiles(parsed, active)
        }
    }
}
