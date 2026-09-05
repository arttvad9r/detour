package dev.triplet.app.vpn

internal const val DETOUR_VPN_ACTION_START = "dev.triplet.app.action.START"
internal const val DETOUR_VPN_ACTION_STOP = "dev.triplet.app.action.STOP"
internal const val DETOUR_VPN_ACTION_RESTART = "dev.triplet.app.action.RESTART"

// Public VpnService.SERVICE_INTERFACE constant, kept here as a plain string so
// command classification remains a local JVM-testable policy function.
internal const val ANDROID_VPN_SERVICE_ACTION = "android.net.VpnService"

internal enum class VpnServiceCommand {
    START_USER,
    START_SYSTEM,
    STOP,
    RESTART,
    IGNORE,
}

internal fun classifyVpnServiceCommand(action: String?): VpnServiceCommand = when (action) {
    DETOUR_VPN_ACTION_START -> VpnServiceCommand.START_USER
    ANDROID_VPN_SERVICE_ACTION -> VpnServiceCommand.START_SYSTEM
    DETOUR_VPN_ACTION_STOP -> VpnServiceCommand.STOP
    DETOUR_VPN_ACTION_RESTART -> VpnServiceCommand.RESTART
    else -> VpnServiceCommand.IGNORE
}
