package dev.triplet.app.vpn

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
