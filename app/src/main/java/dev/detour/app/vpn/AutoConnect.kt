package dev.detour.app.vpn

import dev.detour.app.core.AppRoute
import dev.detour.app.core.ParseResult
import dev.detour.app.core.VlessKeyParser
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.TriSettings

fun canAutoConnect(
    settings: TriSettings,
    vpnPermissionGranted: Boolean,
    effective: EffectiveRoutes,
    activeVpnValid: Boolean = settings.activeVpnConfigured,
): Boolean {
    if (!settings.autoConnect || !vpnPermissionGranted) return false
    if (effective.isEmpty) return false
    return effective.vpnPackages.isEmpty() || activeVpnValid
}

internal fun autoConnectProfileValid(settings: TriSettings): Boolean = when (settings.activeVpn) {
    VpnProfileKind.VLESS -> settings.vlessKeys.active?.uri?.let { uri ->
        val parsed = VlessKeyParser.parse(uri) as? ParseResult.Ok
        parsed?.profile?.isSubscription == false
    } == true
    VpnProfileKind.SUBSCRIPTION -> settings.vlessKeys.active?.uri?.let { uri ->
        val parsed = VlessKeyParser.parse(uri) as? ParseResult.Ok
        parsed?.profile?.isSubscription == true
    } == true
    VpnProfileKind.WARP -> settings.warpProfile != null
}

class AutoConnectCoordinator(
    private val loadSettings: suspend () -> TriSettings,
    private val resolveRoutes: suspend (Map<String, AppRoute>) -> EffectiveRoutes,
    private val vpnPermissionGranted: () -> Boolean,
    private val currentVpnState: () -> VpnState,
    private val startVpn: () -> Unit,
) {
    suspend fun runOnce(): Boolean {
        val settings = loadSettings()
        if (!settings.autoConnect) return false
        if (currentVpnState() != VpnState.Idle) return false
        if (!vpnPermissionGranted()) return false

        val effective = resolveRoutes(settings.routes)
        if (effective.isEmpty) return false
        val activeVpnValid = effective.vpnPackages.isEmpty() || autoConnectProfileValid(settings)
        val shouldStart = canAutoConnect(
            settings = settings,
            vpnPermissionGranted = true,
            effective = effective,
            activeVpnValid = activeVpnValid,
        )
        // Re-check after route resolution so another start path cannot race this one.
        if (!shouldStart || currentVpnState() != VpnState.Idle) return false
        startVpn()
        return true
    }
}
