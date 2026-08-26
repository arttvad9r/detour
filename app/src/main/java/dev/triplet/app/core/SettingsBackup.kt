package dev.triplet.app.core

import org.json.JSONArray
import org.json.JSONObject

/** Versioned, validated user-settings export. Runtime session state is excluded. */
object SettingsBackup {
    const val VERSION = 2
    private const val APP = "detour"
    private val themes = setOf("midnight", "ocean", "graphite", "lavenda")

    data class Backup(
        val vlessUri: String = "", // v1 source compatibility
        val presetId: String = "recommended",
        val dpiCustomArgs: String = "",
        val autoConnect: Boolean = false,
        val themeId: String = "lavenda",
        val dnsId: String = "google",
        val dnsCustom: String = "",
        val routes: Map<String, String> = emptyMap(),
        val vlessKeys: VlessKeys = VlessKeys(emptyList(), null),
    )

    fun toJson(b: Backup): String {
        val keys = if (b.vlessKeys.items.isNotEmpty()) b.vlessKeys
        else legacyKeys(b.vlessUri)
        return JSONObject().apply {
            put("v", VERSION)
            put("app", APP)
            put("vlessKeys", JSONObject().apply {
                put("activeId", keys.activeId)
                put("items", JSONArray().apply {
                    keys.items.forEach { put(JSONObject().apply {
                        put("id", it.id); put("name", it.name); put("uri", it.uri)
                    }) }
                })
            })
            put("preset", b.presetId)
            put("customArgs", b.dpiCustomArgs)
            put("autoConnect", b.autoConnect)
            put("theme", b.themeId)
            put("dns", b.dnsId)
            put("dnsCustom", b.dnsCustom)
            put("routes", JSONObject(b.routes))
        }.toString(2)
    }

    fun fromJson(s: String): Backup? = try {
        val o = JSONObject(s)
        if (o.optString("app") != APP) return null
        when (o.optInt("v", 1)) {
            1 -> parseV1(o)
            VERSION -> parseV2(o)
            else -> null
        }
    } catch (_: Exception) { null }

    private fun parseV1(o: JSONObject): Backup {
        val b = base(o)
        require(b.presetId in DpiPreset.entries.map { it.id } || b.presetId == "compatible")
        require(b.themeId in themes)
        require(DnsOptions.isSelectionValid(b.dnsId, b.dnsCustom))
        require(DpiArgs.isValid(b.dpiCustomArgs) || b.dpiCustomArgs.isBlank())
        val keys = legacyKeys(b.vlessUri)
        validateKeys(keys)
        validateRoutes(b.routes)
        return b.copy(vlessKeys = keys)
    }

    private fun parseV2(o: JSONObject): Backup {
        val b = base(o)
        val keysObject = o.optJSONObject("vlessKeys") ?: throw IllegalArgumentException("missing keys")
        val keys = VlessKeys.fromJson(keysObject.toString())
        require(b.presetId in DpiPreset.entries.map { it.id } || b.presetId == "compatible")
        require(b.themeId in themes)
        require(DnsOptions.isSelectionValid(b.dnsId, b.dnsCustom))
        require(DpiArgs.isValid(b.dpiCustomArgs) || b.dpiCustomArgs.isBlank())
        validateKeys(keys)
        validateRoutes(b.routes)
        return b.copy(vlessKeys = keys, vlessUri = keys.active?.uri ?: "")
    }

    private fun validateKeys(keys: VlessKeys) {
        keys.items.forEach { require(VlessKeyParser.parse(it.uri) is ParseResult.Ok) }
        require(keys.activeId == null || keys.items.any { it.id == keys.activeId })
    }

    private fun validateRoutes(routes: Map<String, String>) {
        routes.forEach { (pkg, route) ->
            require(pkg.isNotBlank() && route in AppRoute.entries.map { it.name })
        }
    }

    private fun base(o: JSONObject): Backup {
        val routes = o.optJSONObject("routes")?.let { ro ->
            buildMap { ro.keys().forEach { put(it, ro.getString(it)) } }
        } ?: emptyMap()
        return Backup(
            vlessUri = o.optString("vless"),
            presetId = o.optString("preset", "recommended"),
            dpiCustomArgs = o.optString("customArgs"),
            autoConnect = o.optBoolean("autoConnect", false),
            themeId = o.optString("theme").takeIf { it.isNotBlank() } ?: "lavenda",
            dnsId = o.optString("dns").takeIf { it.isNotBlank() } ?: "google",
            dnsCustom = o.optString("dnsCustom"),
            routes = routes,
        )
    }

    private fun legacyKeys(uri: String): VlessKeys = if (uri.isBlank()) VlessKeys(emptyList(), null)
    else VlessKeys(listOf(VlessKey("legacy", "VLESS", uri)), "legacy")
}
