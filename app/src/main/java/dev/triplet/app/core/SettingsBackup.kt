package dev.triplet.app.core

import org.json.JSONArray
import org.json.JSONObject

/** Versioned, validated user-settings export. Runtime session state is excluded. */
object SettingsBackup {
    const val VERSION = 3
    const val MAX_BYTES = 1024 * 1024
    private const val APP = "detour"
    private val themes = setOf(
        "catppuccin_latte", "catppuccin_mocha", "gruvbox_dark", "dracula",
        // Legacy ids remain accepted so old Detour backups continue to import.
        "midnight", "ocean", "graphite", "lavenda",
    )

    data class Backup(
        val vlessUri: String = "", // v1 source compatibility
        val presetId: String = "recommended",
        val dpiCustomArgs: String = "",
        val autoConnect: Boolean = false,
        val themeId: String = "catppuccin_latte",
        val dnsId: String = "google",
        val dnsCustom: String = "",
        val routes: Map<String, String> = emptyMap(),
        val vlessKeys: VlessKeys = VlessKeys(emptyList(), null),
        val warpProfile: WarpProfile? = null,
        val activeVpn: VpnProfileKind = VpnProfileKind.VLESS,
        val showSystemApps: Boolean = false,
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
            put("warpProfile", b.warpProfile?.let { JSONObject(it.toJson()) } ?: JSONObject.NULL)
            put("activeVpn", b.activeVpn.name)
            put("preset", b.presetId)
            put("customArgs", b.dpiCustomArgs)
            put("autoConnect", b.autoConnect)
            put("theme", b.themeId)
            put("dns", b.dnsId)
            put("dnsCustom", b.dnsCustom)
            put("routes", JSONObject(b.routes))
            put("showSystemApps", b.showSystemApps)
        }.toString(2)
    }

    fun fromJson(s: String): Backup? = try {
        if (s.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return null
        val o = JSONObject(s)
        if (o.optString("app") != APP) return null
        when (o.optInt("v", 1)) {
            1 -> parseV1(o)
            2 -> parseV2(o)
            VERSION -> parseV3(o)
            else -> null
        }
    } catch (_: Exception) { null }

    private fun parseV1(o: JSONObject): Backup {
        val b = base(o)
        validateBase(b)
        val keys = legacyKeys(b.vlessUri)
        validateKeys(keys)
        validateRoutes(b.routes)
        return b.copy(vlessKeys = keys, activeVpn = VpnProfileKind.VLESS)
    }

    private fun parseV2(o: JSONObject): Backup {
        val b = base(o)
        val keysObject = o.optJSONObject("vlessKeys") ?: throw IllegalArgumentException("missing keys")
        val keys = VlessKeys.fromJson(keysObject.toString())
        validateBase(b)
        validateKeys(keys)
        validateRoutes(b.routes)
        return b.copy(
            vlessKeys = keys,
            vlessUri = keys.active?.uri ?: "",
            activeVpn = VpnProfileKind.VLESS,
        )
    }

    private fun parseV3(o: JSONObject): Backup {
        val b = base(o)
        val keysObject = o.optJSONObject("vlessKeys") ?: throw IllegalArgumentException("missing keys")
        val keys = VlessKeys.fromJson(keysObject.toString())
        val warp = o.optJSONObject("warpProfile")?.let { WarpProfile.fromJson(it.toString()) }
        val activeVpn = VpnProfileKind.fromStored(o.optString("activeVpn"))
        validateBase(b)
        validateKeys(keys)
        validateRoutes(b.routes)
        if (activeVpn == VpnProfileKind.WARP) require(warp != null)
        return b.copy(
            vlessKeys = keys,
            vlessUri = keys.active?.uri ?: "",
            warpProfile = warp,
            activeVpn = activeVpn,
        )
    }

    private fun validateBase(b: Backup) {
        require(b.presetId in DpiPreset.entries.map { it.id } || b.presetId == "compatible")
        require(b.themeId in themes)
        require(DnsOptions.isSelectionValid(b.dnsId, b.dnsCustom))
        require(DpiArgs.isValid(b.dpiCustomArgs) || b.dpiCustomArgs.isBlank())
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
            themeId = o.optString("theme").takeIf { it.isNotBlank() } ?: "catppuccin_latte",
            dnsId = o.optString("dns").takeIf { it.isNotBlank() } ?: "google",
            dnsCustom = o.optString("dnsCustom"),
            routes = routes,
            showSystemApps = o.optBoolean("showSystemApps", false),
        )
    }

    private fun legacyKeys(uri: String): VlessKeys = if (uri.isBlank()) VlessKeys(emptyList(), null)
    else VlessKeys(listOf(VlessKey("legacy", "VLESS", uri)), "legacy")
}
