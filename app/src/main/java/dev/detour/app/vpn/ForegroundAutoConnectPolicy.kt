package dev.detour.app.vpn

import dev.detour.app.data.TriSettings

internal enum class AutoConnectTrigger { APP_LAUNCH, WIFI, CELLULAR }

internal fun foregroundAutoConnectTrigger(
    hasInternet: Boolean,
    validated: Boolean,
    vpnTransport: Boolean,
    wifiTransport: Boolean,
    cellularTransport: Boolean,
): AutoConnectTrigger? {
    if (!hasInternet || !validated || vpnTransport) return null
    return when {
        wifiTransport -> AutoConnectTrigger.WIFI
        cellularTransport -> AutoConnectTrigger.CELLULAR
        else -> null
    }
}

internal fun isAutoConnectEnabled(
    settings: TriSettings,
    trigger: AutoConnectTrigger,
): Boolean = when (trigger) {
    AutoConnectTrigger.APP_LAUNCH -> settings.autoConnect
    AutoConnectTrigger.WIFI -> settings.autoConnectWifi
    AutoConnectTrigger.CELLULAR -> settings.autoConnectCellular
}
