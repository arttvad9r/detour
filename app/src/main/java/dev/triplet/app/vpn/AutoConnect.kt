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
    val usesVpn = effective.vpnPackages.isNotEmpty() ||
        settings.destinationRules.any { it.route == AppRoute.VPN }
    return !usesVpn || activeVpnValid
}

internal fun autoConnectProfileValid(settings: TriSettings): Boolean {
    val exitValid = when (settings.activeVpn) {
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
    if (!exitValid) return false

    // Use the same fail-closed resolver as TriVpnService so automatic starts do
    // not knowingly enter Starting only to fail on a stale, conflicting, or
    // unsupported entry hop. A null entry is a valid one-hop configuration.
    return runCatching {
        resolveMultiHopEntry(settings)
        true
    }.getOrDefault(false)
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
        val usesVpn = effective.vpnPackages.isNotEmpty() ||
            settings.destinationRules.any { it.route == AppRoute.VPN }
        val activeVpnValid = !usesVpn || autoConnectProfileValid(settings)
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
