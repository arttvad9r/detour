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
    enabled: Boolean = settings.autoConnect,
): Boolean {
    if (!enabled || !vpnPermissionGranted) return false
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

internal class AutoConnectCoordinator(
    private val loadSettings: suspend () -> TriSettings,
    private val resolveRoutes: suspend (Map<String, AppRoute>) -> EffectiveRoutes,
    private val vpnPermissionGranted: () -> Boolean,
    private val currentVpnState: () -> VpnState,
    private val startVpn: () -> Unit,
    private val trigger: AutoConnectTrigger = AutoConnectTrigger.APP_LAUNCH,
) {
    suspend fun runOnce(): Boolean {
        val settings = loadSettings()
        val enabled = isAutoConnectEnabled(settings, trigger)
        if (!enabled) return false
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
            enabled = enabled,
        )
        // Re-check after route resolution so another start path cannot race this one.
        if (!shouldStart || currentVpnState() != VpnState.Idle) return false
        startVpn()
        return true
    }
}
