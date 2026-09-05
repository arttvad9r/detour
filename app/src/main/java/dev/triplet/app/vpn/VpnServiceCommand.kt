package dev.triplet.app.vpn

internal const val DETOUR_VPN_ACTION_START = "dev.triplet.app.action.START"
internal const val DETOUR_VPN_ACTION_STOP = "dev.triplet.app.action.STOP"
internal const val DETOUR_VPN_ACTION_RESTART = "dev.triplet.app.action.RESTART"
internal const val DETOUR_VPN_EXTRA_STARTED_BY_APP = "dev.triplet.app.extra.STARTED_BY_APP"

// Public VpnService.SERVICE_INTERFACE constant. The system is not required to
// preserve this action when it starts an Always-on service, so classification
// relies on the explicit app marker plus the service's isAlwaysOn state.
internal const val ANDROID_VPN_SERVICE_ACTION = "android.net.VpnService"

internal enum class VpnServiceCommand {
    START_USER,
    START_SYSTEM,
    STOP,
    RESTART,
    IGNORE,
}

internal fun classifyVpnServiceCommand(
    action: String?,
    startedByApp: Boolean,
    alwaysOn: Boolean,
): VpnServiceCommand = when (action) {
    DETOUR_VPN_ACTION_STOP -> VpnServiceCommand.STOP
    DETOUR_VPN_ACTION_RESTART -> VpnServiceCommand.RESTART
    DETOUR_VPN_ACTION_START -> when {
        startedByApp -> VpnServiceCommand.START_USER
        alwaysOn -> VpnServiceCommand.START_SYSTEM
        else -> VpnServiceCommand.IGNORE
    }
    else -> when {
        !startedByApp && alwaysOn -> VpnServiceCommand.START_SYSTEM
        else -> VpnServiceCommand.IGNORE
    }
}
