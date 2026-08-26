package dev.triplet.app.vpn

import dev.triplet.app.core.AppRoute
import dev.triplet.app.data.TriSettings

fun canAutoConnect(
    settings: TriSettings,
    vpnPermissionGranted: Boolean,
    effective: EffectiveRoutes = EffectiveRoutes(
        vpnPackages = settings.routes.filterValues { it == AppRoute.VPN }.keys,
        dpiPackages = settings.routes.filterValues { it == AppRoute.DPI }.keys,
    ),
    activeVlessValid: Boolean = settings.vlessKeys.active != null,
): Boolean {
    if (!settings.autoConnect || !vpnPermissionGranted) return false
    val routes = settings.routes.values
    if (effective.isEmpty || routes.none { it == AppRoute.VPN || it == AppRoute.DPI }) return false
    return routes.none { it == AppRoute.VPN } || activeVlessValid
}
