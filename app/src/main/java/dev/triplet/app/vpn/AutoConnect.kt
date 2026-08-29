package dev.triplet.app.vpn

import dev.triplet.app.core.AppRoute
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.TriSettings

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
    VpnProfileKind.VLESS -> settings.vlessKeys.active?.uri?.let {
        VlessKeyParser.parse(it) is ParseResult.Ok
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
        val effective = resolveRoutes(settings.routes)
        val shouldStart = canAutoConnect(
            settings = settings,
            vpnPermissionGranted = vpnPermissionGranted(),
            effective = effective,
            activeVpnValid = autoConnectProfileValid(settings),
        ) && currentVpnState() == VpnState.Idle
        if (shouldStart) startVpn()
        return shouldStart
    }
}
