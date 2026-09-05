package dev.triplet.app.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.DpiAutoDomainPlan
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.DpiStrategyCatalog
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.SettingsBackup
import dev.triplet.app.core.VlessKey
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VlessKeys
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.core.WarpProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

internal fun profileKindForUri(uri: String): VpnProfileKind? {
    val parsed = VlessKeyParser.parse(uri) as? ParseResult.Ok ?: return null
    return if (parsed.profile.isSubscription) VpnProfileKind.SUBSCRIPTION else VpnProfileKind.VLESS
}

internal fun VlessKeys.activeProfileKind(): VpnProfileKind? =
    active?.let { profileKindForUri(it.uri) }

internal fun vpnKindAfterVlessUpdate(
    current: VlessKeys,
    updated: VlessKey,
    selectedKind: VpnProfileKind,
): VpnProfileKind {
    if (selectedKind == VpnProfileKind.WARP || current.activeId != updated.id) return selectedKind
    return requireNotNull(profileKindForUri(updated.uri)) { "invalid VPN profile" }
}

data class TriSettings(
    val vlessKeys: VlessKeys,
    val warpProfile: WarpProfile?,
    val activeVpn: VpnProfileKind,
    val preset: DpiPreset,
    val dpiCustomArgs: String,
    val autoConnect: Boolean,
    val themeId: String,
    val dnsId: String,
    val dnsCustom: String,
    val routes: Map<String, AppRoute>,
    val showSystemApps: Boolean,
    val sessionStartedAt: Long?,
    val dpiAutoCandidateId: String = "",
    val dpiAutoDomainPlan: DpiAutoDomainPlan? = null,
) {
    val vlessUri: String get() = vlessKeys.active?.uri ?: ""
    val activeVpnConfigured: Boolean get() = when (activeVpn) {
        VpnProfileKind.VLESS, VpnProfileKind.SUBSCRIPTION -> vlessKeys.activeProfileKind() == activeVpn
        VpnProfileKind.WARP -> warpProfile?.proxies?.isNotEmpty() == true
    }
}

object RoutesMapping {
    private const val KEY_URI = "vless_uri"
    private const val KEY_KEYS = "vless_keys"
    private const val KEY_WARP_PROFILE = "warp_profile"
    private const val KEY_VPN_KIND = "vpn_profile_kind"
    private const val KEY_PRESET = "dpi_preset"
    private const val KEY_CUSTOM_ARGS = "dpi_custom_args"
    private const val KEY_AUTO_CANDIDATE = "dpi_auto_candidate"
    private const val KEY_AUTO_DOMAIN_PLAN = "dpi_auto_domain_plan"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_THEME = "theme_id"
    private const val KEY_DNS = "dns_id"
    private const val KEY_DNS_CUSTOM = "dns_custom"
    private const val KEY_SESSION_STARTED = "session_started_at"
    private const val KEY_SHOW_SYSTEM = "show_system_apps"
    private const val KEY_VLESS_MIGRATED = "vless_legacy_migrated"
    private const val KEY_SENSITIVE_MIGRATED = "sensitive_storage_v1_migrated"
    private const val PREFIX_ROUTE = "route:"

    /** Pure mapping DataStore-snapshot -> settings. JVM-tested. */
    fun toSettings(entries: Map<String, Any?>): TriSettings {
        val vlessKeys = VlessKeys.fromStored(
            entries[KEY_KEYS] as? String ?: "",
            entries[KEY_URI] as? String ?: "",
        )
        val warpProfile = WarpProfile.fromStored(entries[KEY_WARP_PROFILE] as? String ?: "")
        // Preserve the requested kind even when its snapshot is missing/corrupt.
        // Falling back to another configured profile could silently change the
        // endpoint used by auto-connect; an unconfigured selection instead fails closed.
        val activeVpn = VpnProfileKind.fromStored(entries[KEY_VPN_KIND] as? String)
        return TriSettings(
            vlessKeys = vlessKeys,
            warpProfile = warpProfile,
            activeVpn = activeVpn,
            preset = DpiPreset.byId(entries[KEY_PRESET] as? String ?: ""),
            dpiCustomArgs = entries[KEY_CUSTOM_ARGS] as? String ?: "",
            autoConnect = entries[KEY_AUTO_CONNECT] as? Boolean ?: false,
            themeId = entries[KEY_THEME] as? String ?: "",
            dnsId = entries[KEY_DNS] as? String ?: "",
            dnsCustom = entries[KEY_DNS_CUSTOM] as? String ?: "",
            routes = entries.mapNotNull { (k, v) ->
                if (k.startsWith(PREFIX_ROUTE) && v is String && v != AppRoute.DIRECT.name) {
                    AppRoute.entries.firstOrNull { it.name == v }?.let { k.removePrefix(PREFIX_ROUTE) to it }
                } else null
            }.toMap(),
            showSystemApps = entries[KEY_SHOW_SYSTEM] as? Boolean ?: false,
            sessionStartedAt = entries[KEY_SESSION_STARTED] as? Long,
            dpiAutoCandidateId = entries[KEY_AUTO_CANDIDATE] as? String ?: "",
            dpiAutoDomainPlan = DpiAutoDomainPlan.fromStored(
                entries[KEY_AUTO_DOMAIN_PLAN] as? String ?: "",
            ),
        )
    }

    fun routeKey(pkg: String) = stringPreferencesKey(PREFIX_ROUTE + pkg)
    fun uriKey() = stringPreferencesKey(KEY_URI)
    fun keysKey() = stringPreferencesKey(KEY_KEYS)
    fun warpProfileKey() = stringPreferencesKey(KEY_WARP_PROFILE)
    fun vpnKindKey() = stringPreferencesKey(KEY_VPN_KIND)
    fun presetKey() = stringPreferencesKey(KEY_PRESET)
    fun autoConnectKey() = booleanPreferencesKey(KEY_AUTO_CONNECT)
    fun themeKey() = stringPreferencesKey(KEY_THEME)
    fun dnsKey() = stringPreferencesKey(KEY_DNS)
    fun dnsCustomKey() = stringPreferencesKey(KEY_DNS_CUSTOM)
    fun customArgsKey() = stringPreferencesKey(KEY_CUSTOM_ARGS)
    fun autoCandidateKey() = stringPreferencesKey(KEY_AUTO_CANDIDATE)
    fun autoDomainPlanKey() = stringPreferencesKey(KEY_AUTO_DOMAIN_PLAN)
    fun sessionStartedAtKey() = longPreferencesKey(KEY_SESSION_STARTED)
    fun showSystemKey() = booleanPreferencesKey(KEY_SHOW_SYSTEM)
    fun vlessMigratedKey() = booleanPreferencesKey(KEY_VLESS_MIGRATED)
    fun sensitiveMigratedKey() = booleanPreferencesKey(KEY_SENSITIVE_MIGRATED)
}

data class AppInfo(val packageName: String, val label: String, val isSystem: Boolean)

class RoutesStore(context: Context) {
    private val cipher = SensitiveSettingsCipher()
    private val store = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("triplet_settings") },
        migrations = listOf(object : DataMigration<Preferences> {
            override suspend fun shouldMigrate(currentData: Preferences) =
                currentData[RoutesMapping.vlessMigratedKey()] != true ||
                    currentData[RoutesMapping.sensitiveMigratedKey()] != true

            override suspend fun migrate(currentData: Preferences): Preferences =
                currentData.toMutablePreferences().apply {
                    val keysKey = RoutesMapping.keysKey()
                    val uriKey = RoutesMapping.uriKey()
                    val warpKey = RoutesMapping.warpProfileKey()
                    val storedKeys = get(keysKey)

                    if (storedKeys.isNullOrBlank()) {
                        val storedLegacy = get(uriKey)
                        val legacyUri = storedLegacy?.let {
                            cipher.decryptLegacyCompatible(uriKey.name, it)
                        }.orEmpty()
                        val keys = VlessKeys.fromStored("", legacyUri)
                        if (keys.items.isNotEmpty()) {
                            this[keysKey] = cipher.encrypt(keysKey.name, keys.toJson())
                        }
                        // Canonical modern storage is vless_keys. Never retain a
                        // second copy of the active credential after migration.
                        remove(uriKey)
                    } else {
                        this[keysKey] = cipher.encryptIfNeeded(keysKey.name, storedKeys)
                        remove(uriKey)
                    }

                    get(warpKey)?.takeIf { it.isNotBlank() }?.let { warp ->
                        this[warpKey] = cipher.encryptIfNeeded(warpKey.name, warp)
                    }
                    this[RoutesMapping.vlessMigratedKey()] = true
                    this[RoutesMapping.sensitiveMigratedKey()] = true
                }

            override suspend fun cleanUp() = Unit
        }),
    )

    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val settingsData = store.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map<Preferences, TriSettings?> { prefs ->
            RoutesMapping.toSettings(decodedEntries(prefs))
        }

    /**
     * Shared cached settings snapshot. New screens receive the latest DataStore value
     * immediately instead of rendering a temporary null/default state for one frame.
     */
    val settings: StateFlow<TriSettings?> = settingsData
        .stateIn(settingsScope, SharingStarted.Eagerly, null)

    /** Reads the latest committed DataStore value instead of the asynchronously updated UI cache. */
    suspend fun snapshot(): TriSettings = settingsData.filterNotNull().first()

    suspend fun setVlessUri(uri: String) = store.edit { prefs ->
        val key = RoutesMapping.uriKey()
        if (uri.isBlank()) prefs.remove(key) else prefs[key] = cipher.encrypt(key.name, uri)
    }

    suspend fun setSessionStartedAt(value: Long?) = store.edit {
        if (value == null) it.remove(RoutesMapping.sessionStartedAtKey()) else it[RoutesMapping.sessionStartedAtKey()] = value
    }
    suspend fun setShowSystemApps(value: Boolean) = store.edit { it[RoutesMapping.showSystemKey()] = value }
    suspend fun setVlessKeys(keys: VlessKeys) = store.edit { prefs ->
        validateVlessKeys(keys)
        writeVlessKeys(prefs, keys)
    }
    suspend fun setActiveVlessKey(id: String) = store.edit { prefs ->
        val current = readVlessKeys(prefs)
        require(current.items.any { it.id == id }) { "VPN profile is not configured" }
        val next = current.copy(activeId = id)
        validateVlessKeys(next)
        val kind = next.activeProfileKind() ?: throw IllegalArgumentException("invalid VPN profile")
        writeVlessKeys(prefs, next)
        prefs[RoutesMapping.vpnKindKey()] = kind.name
    }
    suspend fun addVlessKey(key: VlessKey) {
        // Adding a profile must not silently change the selected endpoint.
        editVless { current -> VlessKeys(current.items + key, current.activeId) }
    }
    suspend fun updateVlessKey(key: VlessKey) = store.edit { prefs ->
        val current = readVlessKeys(prefs)
        require(current.items.any { it.id == key.id }) { "VPN profile is not configured" }
        val selectedKind = VpnProfileKind.fromStored(prefs[RoutesMapping.vpnKindKey()])
        val nextKind = vpnKindAfterVlessUpdate(current, key, selectedKind)
        val next = VlessKeys(current.items.map { if (it.id == key.id) key else it }, current.activeId)
        validateVlessKeys(next)
        writeVlessKeys(prefs, next)
        if (selectedKind != VpnProfileKind.WARP && current.activeId == key.id) {
            prefs[RoutesMapping.vpnKindKey()] = nextKind.name
        }
    }
    suspend fun deleteVlessKey(id: String) {
        editVless { current -> current.delete(id) }
    }
    suspend fun setWarpProfile(profile: WarpProfile, activate: Boolean = false) = store.edit { prefs ->
        // WarpProfile validates every outbound in its initializer; storing only a
        // constructed profile keeps DataStore free of partially-valid configs.
        // Selection is explicit unless a caller deliberately requests activation.
        val key = RoutesMapping.warpProfileKey()
        prefs[key] = cipher.encrypt(key.name, profile.toJson())
        if (activate) prefs[RoutesMapping.vpnKindKey()] = VpnProfileKind.WARP.name
    }
    suspend fun deleteWarpProfile() = store.edit { prefs ->
        // Keep the selected kind unchanged. If WARP was active it becomes an
        // explicit unconfigured selection instead of silently switching to VLESS.
        prefs.remove(RoutesMapping.warpProfileKey())
    }
    suspend fun setActiveVpn(kind: VpnProfileKind) = store.edit { prefs ->
        when (kind) {
            VpnProfileKind.VLESS, VpnProfileKind.SUBSCRIPTION -> require(
                readVlessKeys(prefs).activeProfileKind() == kind,
            ) { "$kind profile is not configured" }
            VpnProfileKind.WARP -> require(
                readWarpProfile(prefs) != null,
            ) { "WARP profile is not configured" }
        }
        prefs[RoutesMapping.vpnKindKey()] = kind.name
    }
    suspend fun setPreset(preset: DpiPreset) = store.edit { it[RoutesMapping.presetKey()] = preset.id }
    suspend fun setCustomArgs(raw: String) = store.edit { it[RoutesMapping.customArgsKey()] = raw }
    suspend fun setAutoCandidateId(id: String) {
        require(DpiStrategyCatalog.byId(id) != null) { "unknown automatic DPI strategy" }
        store.edit { prefs ->
            prefs[RoutesMapping.autoCandidateKey()] = id
            prefs.remove(RoutesMapping.autoDomainPlanKey())
        }
    }
    suspend fun setAutoDomainPlan(plan: DpiAutoDomainPlan) = store.edit { prefs ->
        // compileArgs() performs the same fail-closed candidate and group validation
        // that runtime will use, before any value is committed to DataStore.
        plan.compileArgs()
        prefs[RoutesMapping.autoDomainPlanKey()] = plan.toStored()
        prefs.remove(RoutesMapping.autoCandidateKey())
    }
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

    /** Validated backup is committed in one transaction and replaces old routes. */
    suspend fun restoreBackup(b: SettingsBackup.Backup) = store.edit { prefs ->
        writeVlessKeys(prefs, b.vlessKeys)
        val warpKey = RoutesMapping.warpProfileKey()
        if (b.warpProfile == null) prefs.remove(warpKey)
        else prefs[warpKey] = cipher.encrypt(warpKey.name, b.warpProfile.toJson())
        prefs[RoutesMapping.vpnKindKey()] = b.activeVpn.name
        prefs[RoutesMapping.presetKey()] = b.presetId
        prefs[RoutesMapping.customArgsKey()] = b.dpiCustomArgs
        if (b.dpiAutoCandidateId.isBlank()) prefs.remove(RoutesMapping.autoCandidateKey())
        else prefs[RoutesMapping.autoCandidateKey()] = b.dpiAutoCandidateId
        // v4 backups do not contain structured per-domain plans. Never leave a
        // stale local plan behind when replacing settings from a backup.
        prefs.remove(RoutesMapping.autoDomainPlanKey())
        // Imported settings never start a VPN implicitly; the user must opt in
        // again after reviewing the imported routes and endpoint.
        prefs[RoutesMapping.autoConnectKey()] = false
        prefs[RoutesMapping.themeKey()] = b.themeId
        prefs[RoutesMapping.dnsKey()] = b.dnsId
        prefs[RoutesMapping.dnsCustomKey()] = b.dnsCustom
        prefs[RoutesMapping.showSystemKey()] = b.showSystemApps
        prefs.asMap().keys.filter { it.name.startsWith("route:") }.forEach { prefs.remove(stringPreferencesKey(it.name)) }
        b.routes.forEach { (pkg, route) ->
            if (route != AppRoute.DIRECT.name) prefs[RoutesMapping.routeKey(pkg)] = route
        }
    }

    private suspend fun editVless(
        transform: (VlessKeys) -> VlessKeys,
    ) = store.edit { prefs ->
        val next = transform(readVlessKeys(prefs))
        validateVlessKeys(next)
        writeVlessKeys(prefs, next)
    }

    private fun decodedEntries(prefs: Preferences): Map<String, Any?> {
        val entries = prefs.asMap().mapKeys { (key, _) -> key.name }.toMutableMap()

        fun decode(key: Preferences.Key<String>, failureValue: String) {
            val stored = prefs[key] ?: return
            entries[key.name] = cipher.decrypt(key.name, stored) ?: failureValue
        }

        // A damaged modern VLESS ciphertext must stay nonblank-but-invalid so
        // VlessKeys.fromStored() fails closed instead of consulting legacy shadow state.
        decode(RoutesMapping.keysKey(), "{encrypted-unavailable")
        decode(RoutesMapping.uriKey(), "")
        decode(RoutesMapping.warpProfileKey(), "{encrypted-unavailable")
        return entries
    }

    private fun readVlessKeys(prefs: Preferences): VlessKeys {
        val keysKey = RoutesMapping.keysKey()
        val uriKey = RoutesMapping.uriKey()
        val storedKeys = prefs[keysKey]
        val keysJson = if (storedKeys == null) ""
        else cipher.decrypt(keysKey.name, storedKeys) ?: "{encrypted-unavailable"
        val storedUri = prefs[uriKey]
        val legacyUri = if (storedUri == null) ""
        else cipher.decrypt(uriKey.name, storedUri) ?: ""
        return VlessKeys.fromStored(keysJson, legacyUri)
    }

    private fun readWarpProfile(prefs: Preferences): WarpProfile? {
        val key = RoutesMapping.warpProfileKey()
        val stored = prefs[key] ?: return null
        val json = cipher.decrypt(key.name, stored) ?: return null
        return WarpProfile.fromStored(json)
    }

    private fun writeVlessKeys(prefs: MutablePreferences, keys: VlessKeys) {
        val keysKey = RoutesMapping.keysKey()
        prefs[keysKey] = cipher.encrypt(keysKey.name, keys.toJson())
        // vless_keys is the sole modern source of truth; keeping vless_uri would
        // duplicate the selected credential and weaken corruption fail-closed behavior.
        prefs.remove(RoutesMapping.uriKey())
    }

    private fun validateVlessKeys(keys: VlessKeys) {
        require(keys.items.map { it.id }.distinct().size == keys.items.size)
        require(keys.activeId == null || keys.items.any { it.id == keys.activeId })
        keys.items.forEach { require(VlessKeyParser.parse(it.uri) is ParseResult.Ok) }
    }
}
