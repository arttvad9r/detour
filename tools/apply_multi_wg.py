from pathlib import Path
import re

ROOT = Path('.')

def write(path: str, content: str):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding='utf-8')

def sub(path: str, pattern: str, repl: str, flags=re.S):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    new, count = re.subn(pattern, repl, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f'{path}: expected one match for {pattern!r}, got {count}')
    p.write_text(new, encoding='utf-8')

wireguard_profiles = r'''package dev.detour.app.core

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
'''
write('app/src/main/java/dev/detour/app/core/WireGuardProfiles.kt', wireguard_profiles)

# RoutesStore: parse the legacy key as a collection and add ID-based CRUD while
# retaining warpProfile as an active-profile compatibility projection.
path = 'app/src/main/java/dev/detour/app/data/RoutesStore.kt'
sub(path, r'import dev\.detour\.app\.core\.WarpProfile\n', 'import dev.detour.app.core.WarpProfile\nimport dev.detour.app.core.WireGuardProfiles\n', flags=0)
sub(path, r'''data class TriSettings\(\n    val vlessKeys: VlessKeys,\n    val warpProfile: WarpProfile\?,\n    val activeVpn: VpnProfileKind,\n    val preset: DpiPreset,\n    val dpiCustomArgs: String,\n    val autoConnect: Boolean,\n    val themeId: String,\n    val dnsId: String,\n    val dnsCustom: String,\n    val routes: Map<String, AppRoute>,\n    val showSystemApps: Boolean,\n    val sessionStartedAt: Long\?,\n\) \{\n    val vlessUri: String get\(\) = vlessKeys\.active\?\.uri \?: ""\n    val activeVpnConfigured: Boolean get\(\) = when \(activeVpn\) \{\n        VpnProfileKind\.VLESS, VpnProfileKind\.SUBSCRIPTION -> vlessKeys\.activeProfileKind\(\) == activeVpn\n        VpnProfileKind\.WARP -> warpProfile\?\.proxies\?\.isNotEmpty\(\) == true\n    \}\n\}''', '''data class TriSettings(
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
    val wireGuardProfiles: WireGuardProfiles = WireGuardProfiles.fromLegacy(warpProfile),
) {
    val vlessUri: String get() = vlessKeys.active?.uri ?: ""
    val activeVpnConfigured: Boolean get() = when (activeVpn) {
        VpnProfileKind.VLESS, VpnProfileKind.SUBSCRIPTION -> vlessKeys.activeProfileKind() == activeVpn
        VpnProfileKind.WARP -> wireGuardProfiles.active?.proxies?.isNotEmpty() == true
    }
}''')
sub(path, r'''        val warpProfile = WarpProfile\.fromStored\(entries\[KEY_WARP_PROFILE\] as\? String \?: ""\)''', '''        val wireGuardProfiles = WireGuardProfiles.fromStored(entries[KEY_WARP_PROFILE] as? String ?: "")
        val warpProfile = wireGuardProfiles.active''')
sub(path, r'''            sessionStartedAt = entries\[KEY_SESSION_STARTED\] as\? Long,\n        \)''', '''            sessionStartedAt = entries[KEY_SESSION_STARTED] as? Long,
            wireGuardProfiles = wireGuardProfiles,
        )''')
sub(path, r'''    suspend fun setWarpProfile\(profile: WarpProfile, activate: Boolean = false\) = store\.edit \{ prefs ->.*?    suspend fun setActiveVpn\(kind: VpnProfileKind\) = store\.edit \{ prefs ->\n        when \(kind\) \{\n            VpnProfileKind\.VLESS, VpnProfileKind\.SUBSCRIPTION -> require\(\n                readVlessKeys\(prefs\)\.activeProfileKind\(\) == kind,\n            \) \{ "\$kind profile is not configured" \}\n            VpnProfileKind\.WARP -> require\(\n                readWarpProfile\(prefs\) != null,\n            \) \{ "WARP profile is not configured" \}\n        \}\n        prefs\[RoutesMapping\.vpnKindKey\(\)\] = kind\.name\n    \}''', '''    suspend fun addWireGuardProfile(profile: WarpProfile) = store.edit { prefs ->
        val current = readWireGuardProfiles(prefs)
        require(current.items.none { it.id == profile.id }) { "WireGuard profile already exists" }
        writeWireGuardProfiles(prefs, WireGuardProfiles(current.items + profile, current.activeId))
    }
    suspend fun updateWireGuardProfile(profile: WarpProfile) = store.edit { prefs ->
        val current = readWireGuardProfiles(prefs)
        require(current.items.any { it.id == profile.id }) { "WireGuard profile is not configured" }
        val next = current.copy(items = current.items.map { if (it.id == profile.id) profile else it })
        writeWireGuardProfiles(prefs, next)
    }
    suspend fun deleteWireGuardProfile(id: String) = store.edit { prefs ->
        val current = readWireGuardProfiles(prefs)
        writeWireGuardProfiles(prefs, current.delete(id))
    }
    suspend fun setActiveWireGuardProfile(id: String) = store.edit { prefs ->
        val current = readWireGuardProfiles(prefs)
        require(current.items.any { it.id == id }) { "WireGuard profile is not configured" }
        writeWireGuardProfiles(prefs, current.copy(activeId = id))
        prefs[RoutesMapping.vpnKindKey()] = VpnProfileKind.WARP.name
    }
    suspend fun setWarpProfile(profile: WarpProfile, activate: Boolean = false) = store.edit { prefs ->
        // Compatibility API: upsert by id instead of erasing unrelated profiles.
        val current = readWireGuardProfiles(prefs)
        val exists = current.items.any { it.id == profile.id }
        val items = if (exists) current.items.map { if (it.id == profile.id) profile else it }
        else current.items + profile
        val next = WireGuardProfiles(items, if (activate) profile.id else current.activeId)
        writeWireGuardProfiles(prefs, next)
        if (activate) prefs[RoutesMapping.vpnKindKey()] = VpnProfileKind.WARP.name
    }
    suspend fun deleteWarpProfile() = store.edit { prefs ->
        // Legacy compatibility API removes the collection; new UI deletes by id.
        prefs.remove(RoutesMapping.warpProfileKey())
    }
    suspend fun setActiveVpn(kind: VpnProfileKind) = store.edit { prefs ->
        when (kind) {
            VpnProfileKind.VLESS, VpnProfileKind.SUBSCRIPTION -> require(
                readVlessKeys(prefs).activeProfileKind() == kind,
            ) { "$kind profile is not configured" }
            VpnProfileKind.WARP -> require(
                readWireGuardProfiles(prefs).active != null,
            ) { "WARP profile is not configured" }
        }
        prefs[RoutesMapping.vpnKindKey()] = kind.name
    }''')
sub(path, r'''        val warpKey = RoutesMapping\.warpProfileKey\(\)\n        if \(b\.warpProfile == null\) prefs\.remove\(warpKey\)\n        else prefs\[warpKey\] = cipher\.encrypt\(warpKey\.name, b\.warpProfile\.toJson\(\)\)''', '''        val wireGuardProfiles = if (b.wireGuardProfiles.items.isNotEmpty() || b.wireGuardProfiles.activeId != null) {
            b.wireGuardProfiles
        } else {
            WireGuardProfiles.fromLegacy(b.warpProfile)
        }
        writeWireGuardProfiles(prefs, wireGuardProfiles)''')
sub(path, r'''    private fun readWarpProfile\(prefs: Preferences\): WarpProfile\? \{\n        val key = RoutesMapping\.warpProfileKey\(\)\n        val stored = prefs\[key\] \?: return null\n        val json = cipher\.decrypt\(key\.name, stored\) \?: return null\n        return WarpProfile\.fromStored\(json\)\n    \}\n''', '''    private fun readWireGuardProfiles(prefs: Preferences): WireGuardProfiles {
        val key = RoutesMapping.warpProfileKey()
        val stored = prefs[key] ?: return WireGuardProfiles.empty()
        val json = cipher.decrypt(key.name, stored) ?: return WireGuardProfiles.empty()
        return WireGuardProfiles.fromStored(json)
    }

    private fun writeWireGuardProfiles(prefs: MutablePreferences, profiles: WireGuardProfiles) {
        val key = RoutesMapping.warpProfileKey()
        if (profiles.items.isEmpty()) prefs.remove(key)
        else prefs[key] = cipher.encrypt(key.name, profiles.toJson())
    }
''')

# Settings backup v4 keeps v1-v3 import compatibility and writes the full list.
path = 'app/src/main/java/dev/detour/app/core/SettingsBackup.kt'
sub(path, r'const val VERSION = 3', 'const val VERSION = 4', flags=0)
sub(path, r'''        val warpProfile: WarpProfile\? = null,\n        val activeVpn: VpnProfileKind = VpnProfileKind\.VLESS,\n        val showSystemApps: Boolean = false,''', '''        val warpProfile: WarpProfile? = null,
        val activeVpn: VpnProfileKind = VpnProfileKind.VLESS,
        val showSystemApps: Boolean = false,
        val wireGuardProfiles: WireGuardProfiles = WireGuardProfiles.fromLegacy(warpProfile),''')
sub(path, r'''        return JSONObject\(\)\.apply \{\n            put\("v", VERSION\)\n            put\("app", APP\)\n            put\("vlessKeys", JSONObject\(keys\.toJson\(\)\)\)\n            put\("warpProfile", b\.warpProfile\?\.let \{ JSONObject\(it\.toJson\(\)\) \} \?: JSONObject\.NULL\)''', '''        val wireGuardProfiles = if (b.wireGuardProfiles.items.isNotEmpty() || b.wireGuardProfiles.activeId != null) {
            b.wireGuardProfiles
        } else {
            WireGuardProfiles.fromLegacy(b.warpProfile)
        }
        return JSONObject().apply {
            put("v", VERSION)
            put("app", APP)
            put("vlessKeys", JSONObject(keys.toJson()))
            put("wireGuardProfiles", JSONObject(wireGuardProfiles.toJson()))''')
sub(path, r'''            2 -> parseV2\(o\)\n            VERSION -> parseV3\(o\)''', '''            2 -> parseV2(o)
            3 -> parseV3(o)
            VERSION -> parseV4(o)''')
sub(path, r'''            warpProfile = warp,\n            activeVpn = activeVpn,\n        \)\n    \}\n\n    private fun validateBase''', '''            warpProfile = warp,
            activeVpn = activeVpn,
            wireGuardProfiles = WireGuardProfiles.fromLegacy(warp),
        )
    }

    private fun parseV4(o: JSONObject): Backup {
        val b = base(o)
        val keysObject = o.optJSONObject("vlessKeys") ?: throw IllegalArgumentException("missing keys")
        val keys = VlessKeys.fromJson(keysObject.toString())
        val wireGuardObject = o.optJSONObject("wireGuardProfiles")
            ?: throw IllegalArgumentException("missing WireGuard profiles")
        val wireGuardProfiles = WireGuardProfiles.fromJson(wireGuardObject.toString())
        val activeVpnName = o.optString("activeVpn").takeIf { it.isNotBlank() }
            ?: VpnProfileKind.VLESS.name
        val activeVpn = VpnProfileKind.entries.firstOrNull { it.name == activeVpnName }
            ?: throw IllegalArgumentException("unknown VPN profile kind")
        validateBase(b)
        validateKeys(keys)
        validateRoutes(b.routes)
        when (activeVpn) {
            VpnProfileKind.VLESS -> if (keys.active != null) require(selectedKeyKind(keys) == VpnProfileKind.VLESS)
            VpnProfileKind.SUBSCRIPTION -> require(selectedKeyKind(keys) == VpnProfileKind.SUBSCRIPTION)
            VpnProfileKind.WARP -> require(wireGuardProfiles.active != null)
        }
        return b.copy(
            vlessKeys = keys,
            vlessUri = keys.active?.uri ?: "",
            warpProfile = wireGuardProfiles.active,
            activeVpn = activeVpn,
            wireGuardProfiles = wireGuardProfiles,
        )
    }

    private fun validateBase''')

# Export all WireGuard profiles.
sub('app/src/main/java/dev/detour/app/ui/BackupViewModel.kt', r'''    warpProfile = settings\.warpProfile,\n    activeVpn = settings\.activeVpn,''', '''    warpProfile = settings.warpProfile,
    activeVpn = settings.activeVpn,
    wireGuardProfiles = settings.wireGuardProfiles,''')

# Runtime selects the active WireGuard profile from the list.
sub('app/src/main/java/dev/detour/app/vpn/TriVpnService.kt', r'VpnProfileKind\.WARP -> settings\.warpProfile\?\.let\(VpnOutbound::Warp\)', 'VpnProfileKind.WARP -> settings.wireGuardProfiles.active?.let(VpnOutbound::Warp)', flags=0)

# ProfilesViewModel: ID-aware WireGuard selection and mutation.
path = 'app/src/main/java/dev/detour/app/ui/ProfilesViewModel.kt'
sub(path, r'''    data class Warp\(\n        override val active: Boolean,\n        override val failed: Boolean = false,\n    \) : ProfileDeleteRequest''', '''    data class Warp(
        val profileId: String,
        override val active: Boolean,
        override val failed: Boolean = false,
    ) : ProfileDeleteRequest''')
sub(path, r'''sealed interface ProfileSelection \{\n    data class Vless\(val keyId: String\) : ProfileSelection\n    data object Warp : ProfileSelection\n\}''', '''sealed interface ProfileSelection {
    data class Vless(val keyId: String) : ProfileSelection
    data class Warp(val profileId: String) : ProfileSelection
}''')
sub(path, r'''data class ProfilesUiState\(\n    val vlessItems: List<VlessKey> = emptyList\(\),\n    val activeVlessId: String\? = null,\n    val warpProfile: WarpProfile\? = null,''', '''data class ProfilesUiState(
    val vlessItems: List<VlessKey> = emptyList(),
    val activeVlessId: String? = null,
    val wireGuardProfiles: List<WarpProfile> = emptyList(),
    val activeWireGuardId: String? = null,''')
sub(path, r'''    VpnProfileKind\.WARP -> ProfileSelection\.Warp''', '''    VpnProfileKind.WARP -> settings.wireGuardProfiles.activeId?.let(ProfileSelection::Warp)''')
sub(path, r'''        activeVlessId = when \(selection\) \{\n            is ProfileSelection\.Vless -> selection\.keyId\n            ProfileSelection\.Warp, null -> settings\?\.vlessKeys\?\.activeId\n        \},\n        warpProfile = settings\?\.warpProfile,\n        activeVpn = when \(selection\) \{\n            is ProfileSelection\.Vless -> selectedKeyKind\(settings, selection\.keyId\)\n            ProfileSelection\.Warp -> VpnProfileKind\.WARP\n            null -> settings\?\.activeVpn \?: VpnProfileKind\.VLESS\n        \},''', '''        activeVlessId = when (selection) {
            is ProfileSelection.Vless -> selection.keyId
            is ProfileSelection.Warp, null -> settings?.vlessKeys?.activeId
        },
        wireGuardProfiles = settings?.wireGuardProfiles?.items.orEmpty(),
        activeWireGuardId = when (selection) {
            is ProfileSelection.Warp -> selection.profileId
            else -> settings?.wireGuardProfiles?.activeId
        },
        activeVpn = when (selection) {
            is ProfileSelection.Vless -> selectedKeyKind(settings, selection.keyId)
            is ProfileSelection.Warp -> VpnProfileKind.WARP
            null -> settings?.activeVpn ?: VpnProfileKind.VLESS
        },''')
sub(path, r'''internal fun warpDeleteRequest\(\n    settings: TriSettings\?,\n    selectionOverride: ProfileSelection\? = null,\n\): ProfileDeleteRequest\.Warp \{\n    val state = profilesUiState\(settings, selectionOverride = selectionOverride\)\n    return ProfileDeleteRequest\.Warp\(active = state\.activeVpn == VpnProfileKind\.WARP\)\n\}''', '''internal fun warpDeleteRequest(
    settings: TriSettings?,
    profileId: String,
    selectionOverride: ProfileSelection? = null,
): ProfileDeleteRequest.Warp {
    val state = profilesUiState(settings, selectionOverride = selectionOverride)
    return ProfileDeleteRequest.Warp(
        profileId = profileId,
        active = state.activeVpn == VpnProfileKind.WARP && state.activeWireGuardId == profileId,
    )
}''')
sub(path, r'''internal fun warpMutationTunnelAction\(\n    activeVpn: VpnProfileKind,\n    deleting: Boolean,\n\): ProfileTunnelAction \{\n    if \(activeVpn != VpnProfileKind\.WARP\) return ProfileTunnelAction\.NONE\n    return if \(deleting\) ProfileTunnelAction\.STOP else ProfileTunnelAction\.RESTART\n\}''', '''internal fun wireGuardMutationTunnelAction(
    activeVpn: VpnProfileKind,
    activeWireGuardId: String?,
    profileId: String,
    deleting: Boolean,
): ProfileTunnelAction {
    if (activeVpn != VpnProfileKind.WARP || activeWireGuardId != profileId) return ProfileTunnelAction.NONE
    return if (deleting) ProfileTunnelAction.STOP else ProfileTunnelAction.RESTART
}

// Retain the old helper for source-compatible tests/callers; production uses the ID-aware variant.
internal fun warpMutationTunnelAction(
    activeVpn: VpnProfileKind,
    deleting: Boolean,
): ProfileTunnelAction = if (activeVpn != VpnProfileKind.WARP) ProfileTunnelAction.NONE
else if (deleting) ProfileTunnelAction.STOP else ProfileTunnelAction.RESTART''')
sub(path, r'''    private val loadWarpConfig: suspend \(String\) -> String\?,\n    private val setWarpProfile: suspend \(WarpProfile\) -> Unit,\n    private val deleteWarpProfile: suspend \(\) -> Unit,\n    private val setActiveVpn: suspend \(VpnProfileKind\) -> Unit,''', '''    private val loadWarpConfig: suspend (String) -> String?,
    private val addWireGuardProfile: suspend (WarpProfile) -> Unit,
    private val updateWireGuardProfile: suspend (WarpProfile) -> Unit,
    private val deleteWireGuardProfile: suspend (String) -> Unit,
    private val setActiveWireGuardProfile: suspend (String) -> Unit,''')
sub(path, r'''    fun importWarpDocument\(uri: String\) \{''', '''    fun importWarpDocument(uri: String, replaceProfileId: String? = null) {''')
sub(path, r'''                    is WarpImportResult\.Ok -> persistWarpProfile\(result\.profile\)''', '''                    is WarpImportResult.Ok -> persistWarpProfile(result.profile, replaceProfileId)''')
sub(path, r'''    fun deleteWarp\(\) \{\n        if \(deleteInFlight\.value\) return\n        _pendingDelete\.value = warpDeleteRequest\(settings\.value, selectionOverride\.value\)\n    \}''', '''    fun deleteWarp(profileId: String) {
        if (deleteInFlight.value) return
        _pendingDelete.value = warpDeleteRequest(settings.value, profileId, selectionOverride.value)
    }''')
sub(path, r'''                        is ProfileDeleteRequest\.Warp -> \{\n                            val tunnelAction = warpMutationTunnelAction\(\n                                profilesUiState\(settings\.value\)\.activeVpn,\n                                deleting = true,\n                            \)\n                            deleteWarpProfile\(\)\n                            if \(settings\.value\?\.warpProfile != null\) \{\n                                settings\.first \{ it\?\.warpProfile == null \}\n                            \}\n                            applyTunnelAction\(tunnelAction\)\n                        \}''', '''                        is ProfileDeleteRequest.Warp -> {
                            val state = profilesUiState(settings.value)
                            val tunnelAction = wireGuardMutationTunnelAction(
                                state.activeVpn,
                                state.activeWireGuardId,
                                request.profileId,
                                deleting = true,
                            )
                            deleteWireGuardProfile(request.profileId)
                            if (settings.value?.wireGuardProfiles?.items?.any { it.id == request.profileId } == true) {
                                settings.first { current ->
                                    current?.wireGuardProfiles?.items?.none { it.id == request.profileId } != false
                                }
                            }
                            applyTunnelAction(tunnelAction)
                        }''')
sub(path, r'''    fun selectWarp\(\) \{\n        selectProfile\(ProfileSelection\.Warp\)\n    \}\n\n    private suspend fun persistWarpProfile\(profile: WarpProfile\) \{\n        profileMutationMutex\.withLock \{\n            val tunnelAction = warpMutationTunnelAction\(\n                profilesUiState\(settings\.value\)\.activeVpn,\n                deleting = false,\n            \)\n            setWarpProfile\(profile\)\n            if \(settings\.value\?\.warpProfile != profile\) \{\n                settings\.first \{ it\?\.warpProfile == profile \}\n            \}\n            applyTunnelAction\(tunnelAction\)\n            warpImportStatus\.value = WarpImportStatus\.IDLE\n            _warpSaved\.emit\(Unit\)\n        \}\n    \}''', '''    fun selectWarp(profileId: String) {
        selectProfile(ProfileSelection.Warp(profileId))
    }

    private suspend fun persistWarpProfile(profile: WarpProfile, replaceProfileId: String? = null) {
        profileMutationMutex.withLock {
            val target = replaceProfileId?.let { profile.copy(id = it) } ?: profile
            val state = profilesUiState(settings.value)
            val tunnelAction = if (replaceProfileId == null) {
                ProfileTunnelAction.NONE
            } else {
                wireGuardMutationTunnelAction(
                    state.activeVpn,
                    state.activeWireGuardId,
                    replaceProfileId,
                    deleting = false,
                )
            }
            if (replaceProfileId == null) addWireGuardProfile(target) else updateWireGuardProfile(target)
            if (settings.value?.wireGuardProfiles?.items?.none { it == target } != false) {
                settings.first { current -> current?.wireGuardProfiles?.items?.any { it == target } == true }
            }
            applyTunnelAction(tunnelAction)
            warpImportStatus.value = WarpImportStatus.IDLE
            _warpSaved.emit(Unit)
        }
    }''')
sub(path, r'''                            is ProfileSelection\.Vless -> setActiveVlessKey\(desired\.keyId\)\n                            ProfileSelection\.Warp -> setActiveVpn\(VpnProfileKind\.WARP\)''', '''                            is ProfileSelection.Vless -> setActiveVlessKey(desired.keyId)
                            is ProfileSelection.Warp -> setActiveWireGuardProfile(desired.profileId)''')
sub(path, r'''                    setWarpProfile = \{ store\.setWarpProfile\(it\) \},\n                    deleteWarpProfile = store::deleteWarpProfile,\n                    setActiveVpn = store::setActiveVpn,''', '''                    addWireGuardProfile = store::addWireGuardProfile,
                    updateWireGuardProfile = store::updateWireGuardProfile,
                    deleteWireGuardProfile = store::deleteWireGuardProfile,
                    setActiveWireGuardProfile = store::setActiveWireGuardProfile,''')

# VlessKeyScreen: show all WireGuard profiles and edit/replace a specific id.
path = 'app/src/main/java/dev/detour/app/ui/VlessKeyScreen.kt'
sub(path, r'''private data class ProfileGroups\(\n    val vless: List<VlessKey>,\n    val subscriptions: List<VlessKey>,\n    val warp: WarpProfile\?,\n\)''', '''private data class ProfileGroups(
    val vless: List<VlessKey>,
    val subscriptions: List<VlessKey>,
    val wireGuard: List<WarpProfile>,
)''')
sub(path, r'''    val warpProfile = state\.warpProfile\n    val activeVpn = state\.activeVpn\n    val activeVlessId = state\.activeVlessId''', '''    val wireGuardProfiles = state.wireGuardProfiles
    val activeWireGuardId = state.activeWireGuardId
    val activeVpn = state.activeVpn
    val activeVlessId = state.activeVlessId''')
sub(path, r'''    val groups = remember\(vlessItems, warpProfile\) \{\n        ProfileGroups\(\n            vless = vlessItems\.filter \{ parsedProfile\(it\)\?\.isSubscription != true \},\n            subscriptions = vlessItems\.filter \{ parsedProfile\(it\)\?\.isSubscription == true \},\n            warp = warpProfile,\n        \)\n    \}''', '''    val groups = remember(vlessItems, wireGuardProfiles) {
        ProfileGroups(
            vless = vlessItems.filter { parsedProfile(it)?.isSubscription != true },
            subscriptions = vlessItems.filter { parsedProfile(it)?.isSubscription == true },
            wireGuard = wireGuardProfiles,
        )
    }''')
sub(path, r'''    var suppressWarpNotice by rememberSaveable \{ mutableStateOf\(false\) \}''', '''    var suppressWarpNotice by rememberSaveable { mutableStateOf(false) }
    var replacingWireGuardId by rememberSaveable { mutableStateOf<String?>(null) }''')
sub(path, r'''    val warpLauncher = rememberLauncherForActivityResult\(ActivityResultContracts\.OpenDocument\(\)\) \{ uri ->\n        uri\?\.let \{\n            suppressWarpNotice = false\n            viewModel\.importWarpDocument\(it\.toString\(\)\)\n        \}\n    \}''', '''    val warpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val replaceId = replacingWireGuardId
        replacingWireGuardId = null
        uri?.let {
            suppressWarpNotice = false
            viewModel.importWarpDocument(it.toString(), replaceProfileId = replaceId)
        }
    }''')
sub(path, r'''                val hasProfiles = groups\.vless\.isNotEmpty\(\) \|\|\n                    groups\.subscriptions\.isNotEmpty\(\) \|\|\n                    groups\.warp != null''', '''                val hasProfiles = groups.vless.isNotEmpty() ||
                    groups.subscriptions.isNotEmpty() ||
                    groups.wireGuard.isNotEmpty()''')
sub(path, r'''                    if \(groups\.warp != null\) \{\n                        if \(groups\.vless\.isNotEmpty\(\) \|\| groups\.subscriptions\.isNotEmpty\(\)\) \{\n                            Spacer\(Modifier\.height\(Spacing\.space16\)\)\n                        \}\n                        ProfileSectionTitle\(stringResource\(R\.string\.profile_section_wireguard\)\)\n                        WarpProfileList\(\n                            profile = groups\.warp,\n                            selected = activeVpn == VpnProfileKind\.WARP,\n                            importing = warpImporting,\n                            onEdit = \{ warpLauncher\.launch\(arrayOf\("\*/\*"\)\) \},\n                            onDelete = viewModel::deleteWarp,\n                            onClick = \{\n                                haptics\.performHapticFeedback\(HapticFeedbackType\.SegmentTick\)\n                                viewModel\.selectWarp\(\)\n                            \},\n                        \)\n                    \}''', '''                    if (groups.wireGuard.isNotEmpty()) {
                        if (groups.vless.isNotEmpty() || groups.subscriptions.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.space16))
                        }
                        ProfileSectionTitle(stringResource(R.string.profile_section_wireguard))
                        WireGuardProfileList(
                            profiles = groups.wireGuard,
                            activeWireGuardId = activeWireGuardId,
                            activeVpn = activeVpn,
                            importing = warpImporting,
                            onEdit = { profileId ->
                                replacingWireGuardId = profileId
                                warpLauncher.launch(arrayOf("*/*"))
                            },
                            onDelete = viewModel::deleteWarp,
                            onClick = { profileId ->
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                viewModel.selectWarp(profileId)
                            },
                        )
                    }''')
sub(path, r'''                            showAddMenu = false\n                            suppressWarpNotice = false\n                            warpLauncher\.launch\(arrayOf\("\*/\*"\)\)''', '''                            showAddMenu = false
                            suppressWarpNotice = false
                            replacingWireGuardId = null
                            warpLauncher.launch(arrayOf("*/*"))''')
sub(path, r'''@Composable\nprivate fun WarpProfileList\(\n    profile: WarpProfile\?,\n    selected: Boolean,\n    importing: Boolean,\n    onEdit: \(\) -> Unit,\n    onDelete: \(\) -> Unit,\n    onClick: \(\) -> Unit,\n\) \{.*?\n\}\n\n@Composable\nprivate fun CompactProfileRow''', '''@Composable
private fun WireGuardProfileList(
    profiles: List<WarpProfile>,
    activeWireGuardId: String?,
    activeVpn: VpnProfileKind,
    importing: Boolean,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClick: (String) -> Unit,
) {
    if (profiles.isEmpty()) return

    DetourCard(
        Modifier
            .padding(horizontal = Spacing.space16)
            .selectableGroup(),
    ) {
        profiles.forEachIndexed { index, profile ->
            val protocol = when {
                profile.proxies.any { it.amnezia.version == 3 } -> stringResource(R.string.profile_amneziawg_31)
                profile.name.contains("Amnezia", ignoreCase = true) -> stringResource(R.string.profile_amneziawg)
                else -> stringResource(R.string.protocol_warp)
            }
            val selected = activeVpn == VpnProfileKind.WARP && activeWireGuardId == profile.id
            CompactProfileRow(
                title = profile.name,
                subtitle = stringResource(R.string.profile_wireguard_row_subtitle, protocol, profile.proxies.size),
                selected = selected,
                busy = importing,
                editDescription = stringResource(R.string.key_edit),
                deleteDescription = stringResource(R.string.key_delete),
                onEdit = { onEdit(profile.id) },
                onDelete = { onDelete(profile.id) },
                onClick = { if (!selected) onClick(profile.id) },
            )
            if (index < profiles.lastIndex) GroupDivider(startInset = 52)
        }
    }
}

@Composable
private fun CompactProfileRow''')

# New focused JVM tests: legacy single-profile compatibility, multi-profile
# round-trip, and selection-safe deletion.
test = r'''package dev.detour.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardProfilesTest {
    private fun profile(id: String, name: String, version: Int? = null): WarpProfile = WarpProfile(
        id = id,
        name = name,
        proxies = listOf(
            WarpProxy(
                name = name,
                server = "203.0.113.10",
                port = 51820,
                ip = "10.0.0.2",
                privateKey = "private-key-$id",
                publicKey = "public-key-$id",
                reserved = emptyList(),
                allowedIps = listOf("0.0.0.0/0"),
                amnezia = AmneziaWgOptions(version = version),
            ),
        ),
    )

    @Test fun legacySingleProfileBecomesSelectedCollectionItem() {
        val legacy = profile("warp-1", "Cloudflare WARP")
        val parsed = WireGuardProfiles.fromStored(legacy.toJson())

        assertEquals(listOf(legacy), parsed.items)
        assertEquals(legacy.id, parsed.activeId)
        assertEquals(legacy, parsed.active)
    }

    @Test fun multipleProfilesRoundTripWithoutReplacingEachOther() {
        val warp = profile("warp-1", "Cloudflare WARP")
        val awg = profile("awg-1", "My VPS", version = 3)
        val original = WireGuardProfiles(listOf(warp, awg), awg.id)

        val restored = WireGuardProfiles.fromStored(original.toJson())

        assertEquals(original, restored)
        assertEquals(2, restored.items.size)
        assertEquals(awg, restored.active)
    }

    @Test fun deletingInactiveProfileKeepsSelectionAndDeletingActiveFailsClosed() {
        val warp = profile("warp-1", "Cloudflare WARP")
        val awg = profile("awg-1", "My VPS", version = 3)
        val profiles = WireGuardProfiles(listOf(warp, awg), awg.id)

        val withoutWarp = profiles.delete(warp.id)
        assertEquals(awg.id, withoutWarp.activeId)
        assertEquals(awg, withoutWarp.active)

        val withoutActive = profiles.delete(awg.id)
        assertNull(withoutActive.activeId)
        assertNull(withoutActive.active)
        assertTrue(withoutActive.items.contains(warp))
    }
}
'''
write('app/src/test/java/dev/detour/app/core/WireGuardProfilesTest.kt', test)

# The patching mechanism is temporary and must never reach main.
Path('tools/apply_multi_wg.py').unlink(missing_ok=True)
Path('.github/workflows/apply-multi-wg.yml').unlink(missing_ok=True)
