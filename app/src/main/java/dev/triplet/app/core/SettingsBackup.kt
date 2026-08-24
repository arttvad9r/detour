package dev.triplet.app.core

import org.json.JSONObject

/** Экспорт/импорт настроек в JSON (без секретов движка — только то, что вводит пользователь). */
object SettingsBackup {

    data class Backup(
        val vlessUri: String,
        val presetId: String,
        val dpiCustomArgs: String,
        val autoConnect: Boolean,
        val themeId: String,
        val dnsId: String,
        val dnsCustom: String,
        val routes: Map<String, String>, // pkg -> AppRoute.name
    )

    fun toJson(b: Backup): String = JSONObject().apply {
        put("v", 1)
        put("app", "detour")
        put("vless", b.vlessUri)
        put("preset", b.presetId)
        put("customArgs", b.dpiCustomArgs)
        put("autoConnect", b.autoConnect)
        put("theme", b.themeId)
        put("dns", b.dnsId)
        put("dnsCustom", b.dnsCustom)
        put("routes", JSONObject(b.routes))
    }.toString(2)

    fun fromJson(s: String): Backup? = runCatching {
        val o = JSONObject(s)
        if (o.optString("app") != "detour") return null
        val routes = o.optJSONObject("routes")?.let { ro ->
            buildMap { ro.keys().forEach { k -> put(k, ro.getString(k)) } }
        } ?: emptyMap()
        Backup(
            vlessUri = o.optString("vless"),
            presetId = o.optString("preset", "recommended"),
            dpiCustomArgs = o.optString("customArgs"),
            autoConnect = o.optBoolean("autoConnect", false),
            themeId = o.optString("theme", "lavenda"),
            dnsId = o.optString("dns", "google"),
            dnsCustom = o.optString("dnsCustom"),
            routes = routes,
        )
    }.getOrNull()
}
