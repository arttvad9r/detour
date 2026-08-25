package dev.triplet.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class TriSettings(
    val vlessUri: String,
    val preset: DpiPreset,
    val dpiCustomArgs: String,
    val autoConnect: Boolean,
    val themeId: String,
    val dnsId: String,
    val dnsCustom: String,
    val routes: Map<String, AppRoute>,
)

object RoutesMapping {
    private const val KEY_URI = "vless_uri"
    private const val KEY_PRESET = "dpi_preset"
    private const val KEY_CUSTOM_ARGS = "dpi_custom_args"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_THEME = "theme_id"
    private const val KEY_DNS = "dns_id"
    private const val KEY_DNS_CUSTOM = "dns_custom"
    private const val PREFIX_ROUTE = "route:"

    /** Pure mapping DataStore-snapshot -> settings. JVM-tested. */
    fun toSettings(entries: Map<String, Any?>): TriSettings = TriSettings(
        vlessUri = entries[KEY_URI] as? String ?: "",
        preset = DpiPreset.byId(entries[KEY_PRESET] as? String ?: ""),
        dpiCustomArgs = entries[KEY_CUSTOM_ARGS] as? String ?: "",
        autoConnect = entries[KEY_AUTO_CONNECT] as? Boolean ?: false,
        themeId = entries[KEY_THEME] as? String ?: "",
        dnsId = entries[KEY_DNS] as? String ?: "",
        dnsCustom = entries[KEY_DNS_CUSTOM] as? String ?: "",
        routes = entries.mapNotNull { (k, v) ->
            if (k.startsWith(PREFIX_ROUTE) && v is String && v != AppRoute.DIRECT.name) {
                k.removePrefix(PREFIX_ROUTE) to AppRoute.valueOf(v)
            } else null
        }.toMap(),
    )

    fun routeKey(pkg: String) = stringPreferencesKey(PREFIX_ROUTE + pkg)
    fun uriKey() = stringPreferencesKey(KEY_URI)
    fun presetKey() = stringPreferencesKey(KEY_PRESET)
    fun autoConnectKey() = booleanPreferencesKey(KEY_AUTO_CONNECT)
    fun themeKey() = stringPreferencesKey(KEY_THEME)
    fun dnsKey() = stringPreferencesKey(KEY_DNS)
    fun dnsCustomKey() = stringPreferencesKey(KEY_DNS_CUSTOM)
    fun customArgsKey() = stringPreferencesKey(KEY_CUSTOM_ARGS)
}

data class AppInfo(val packageName: String, val label: String, val isSystem: Boolean)

class RoutesStore(context: Context) {
    private val store = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("triplet_settings")
    }

    val settings: Flow<TriSettings> = store.data.map { prefs ->
        RoutesMapping.toSettings(prefs.asMap().mapKeys { (k, _) -> k.name })
    }

    suspend fun snapshot(): TriSettings = settings.first()

    suspend fun setVlessUri(uri: String) = store.edit { it[RoutesMapping.uriKey()] = uri }
    suspend fun setPreset(preset: DpiPreset) = store.edit { it[RoutesMapping.presetKey()] = preset.id }
    suspend fun setCustomArgs(raw: String) = store.edit { it[RoutesMapping.customArgsKey()] = raw }
    suspend fun setAutoConnect(v: Boolean) = store.edit { it[RoutesMapping.autoConnectKey()] = v }
    suspend fun setTheme(id: String) = store.edit { it[RoutesMapping.themeKey()] = id }
    suspend fun setDns(id: String, custom: String) = store.edit {
        it[RoutesMapping.dnsKey()] = id
        it[RoutesMapping.dnsCustomKey()] = custom
    }
    suspend fun setRoute(pkg: String, route: AppRoute) = store.edit {
        val key = RoutesMapping.routeKey(pkg)
        if (route == AppRoute.DIRECT) it.remove(key) else it[key] = route.name
    }
}
