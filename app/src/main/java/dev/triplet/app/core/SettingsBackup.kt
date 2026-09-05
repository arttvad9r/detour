package dev.triplet.app.core

import org.json.JSONArray
import org.json.JSONObject

/** Versioned, validated user-settings export. Runtime session state is excluded. */
object SettingsBackup {
    const val VERSION = 5
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
        val destinationRules: List<DestinationRule> = emptyList(),
        val vlessKeys: VlessKeys = VlessKeys(emptyList(), null),
        val warpProfile: WarpProfile? = null,
        val activeVpn: VpnProfileKind = VpnProfileKind.VLESS,
        val multiHopEntry: MultiHopEntryRef? = null,
        val showSystemApps: Boolean = false,
    )

    fun toJson(b: Backup): String {
        val keys = if (b.vlessKeys.items.isNotEmpty()) b.vlessKeys
        else legacyKeys(b.vlessUri)
        DestinationRules.validate(b.destinationRules)
        validateMultiHop(b.multiHopEntry, keys, b.warpProfile, b.activeVpn)
        return JSONObject().apply {
            put("v", VERSION)
            put("app", APP)
            put("vlessKeys", JSONObject(keys.toJson()))
            put("warpProfile", b.warpProfile?.let { JSONObject(it.toJson()) } ?: JSONObject.NULL)
            put("activeVpn", b.activeVpn.name)
            put("multiHopEntry", b.multiHopEntry?.let(MultiHopEntryRef::toStored) ?: JSONObject.NULL)
            put("preset", b.presetId)
            put("customArgs", b.dpiCustomArgs)
            put("autoConnect", b.autoConnect)
            put("theme", b.themeId)
            put("dns", b.dnsId)
            put("dnsCustom", b.dnsCustom)
            put("routes", JSONObject(b.routes))
            put("destinationRules", JSONArray(DestinationRules.toJson(b.destinationRules)))
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
            3 -> parseV3(o)
            4 -> parseV4(o)
            VERSION -> parseV5(o)
            else -> null
        }
    } catch (_: Exception) { null }

    private fun parseV1(o: JSONObject): Backup {
        val b = base(o)
        validateBase(b)
        val keys = legacyKeys(b.vlessUri)
        validateKeys(keys)
        validateRoutes(b.routes)
        return b.copy(vlessKeys = keys, activeVpn = selectedKeyKind(keys) ?: VpnProfileKind.VLESS)
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
            activeVpn = selectedKeyKind(keys) ?: VpnProfileKind.VLESS,
        )
    }

    private fun parseV3(o: JSONObject): Backup = parseModern(o, emptyList(), null)

    private fun parseV4(o: JSONObject): Backup {
        val rules = o.optJSONArray("destinationRules")?.let {
            DestinationRules.fromJsonStrict(it.toString())
        } ?: emptyList()
        return parseModern(o, rules, null)
    }

    private fun parseV5(o: JSONObject): Backup {
        val rules = o.optJSONArray("destinationRules")?.let {
            DestinationRules.fromJsonStrict(it.toString())
        } ?: emptyList()
        val entry = when (val raw = o.opt("multiHopEntry")) {
            null, JSONObject.NULL -> null
            is String -> MultiHopEntryRef.fromStored(raw)
            else -> throw IllegalArgumentException("invalid multi-hop entry")
        }
        require(entry !is MultiHopEntryRef.Invalid) { "invalid multi-hop entry" }
        return parseModern(o, rules, entry)
    }

    private fun parseModern(
        o: JSONObject,
        destinationRules: List<DestinationRule>,
        multiHopEntry: MultiHopEntryRef?,
    ): Backup {
        val b = base(o)
        val keysObject = o.optJSONObject("vlessKeys") ?: throw IllegalArgumentException("missing keys")
        val keys = VlessKeys.fromJson(keysObject.toString())
        val warp = o.optJSONObject("warpProfile")?.let { WarpProfile.fromJson(it.toString()) }
        val activeVpnName = o.optString("activeVpn").takeIf { it.isNotBlank() }
            ?: VpnProfileKind.VLESS.name
        val activeVpn = VpnProfileKind.entries.firstOrNull { it.name == activeVpnName }
            ?: throw IllegalArgumentException("unknown VPN profile kind")
        validateBase(b)
        validateKeys(keys)
        validateRoutes(b.routes)
        DestinationRules.validate(destinationRules)
        when (activeVpn) {
            VpnProfileKind.VLESS -> if (keys.active != null) require(selectedKeyKind(keys) == VpnProfileKind.VLESS)
            VpnProfileKind.SUBSCRIPTION -> require(selectedKeyKind(keys) == VpnProfileKind.SUBSCRIPTION)
            VpnProfileKind.WARP -> require(warp != null)
        }
        validateMultiHop(multiHopEntry, keys, warp, activeVpn)
        return b.copy(
            vlessKeys = keys,
            vlessUri = keys.active?.uri ?: "",
            warpProfile = warp,
            activeVpn = activeVpn,
            multiHopEntry = multiHopEntry,
            destinationRules = destinationRules,
        )
    }

    private fun validateMultiHop(
        entry: MultiHopEntryRef?,
        keys: VlessKeys,
        warp: WarpProfile?,
        activeVpn: VpnProfileKind,
    ) {
        if (entry == null) return
        require(entry !is MultiHopEntryRef.Invalid) { "invalid multi-hop entry" }
        require(!entry.conflictsWithExit(activeVpn, keys.activeId)) {
            "multi-hop entry conflicts with exit"
        }
        when (entry) {
            is MultiHopEntryRef.Vless -> {
                val key = keys.items.singleOrNull { it.id == entry.keyId }
                    ?: throw IllegalArgumentException("multi-hop VLESS entry is unavailable")
                val parsed = VlessKeyParser.parse(key.uri) as? ParseResult.Ok
                    ?: throw IllegalArgumentException("multi-hop VLESS entry is invalid")
                require(!parsed.profile.isSubscription) { "subscription cannot be a multi-hop entry" }
            }
            MultiHopEntryRef.Warp -> require(warp != null) { "multi-hop WARP entry is unavailable" }
            MultiHopEntryRef.Invalid -> error("validated above")
        }
    }

    private fun validateBase(b: Backup) {
        require(b.presetId in DpiPreset.entries.map { it.id } || b.presetId == "compatible")
        require(b.themeId in themes)
        require(DnsOptions.isSelectionValid(b.dnsId, b.dnsCustom))
        if (b.presetId == DpiPreset.CUSTOM.id) require(DpiArgs.isValid(b.dpiCustomArgs))
    }

    private fun validateKeys(keys: VlessKeys) {
        keys.items.forEach { require(VlessKeyParser.parse(it.uri) is ParseResult.Ok) }
        require(keys.activeId == null || keys.items.any { it.id == keys.activeId })
    }

    private fun selectedKeyKind(keys: VlessKeys): VpnProfileKind? {
        val active = keys.active ?: return null
        val parsed = VlessKeyParser.parse(active.uri) as? ParseResult.Ok ?: return null
        return if (parsed.profile.isSubscription) VpnProfileKind.SUBSCRIPTION else VpnProfileKind.VLESS
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
