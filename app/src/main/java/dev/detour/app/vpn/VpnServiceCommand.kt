package dev.detour.app.vpn

import android.net.VpnService

internal enum class VpnServiceCommand { START, STOP, RESTART, IGNORE }

/**
 * The service is not exported and requires BIND_VPN_SERVICE, so the explicit
 * Detour actions can only come from the app and the platform's
 * [VpnService.SERVICE_INTERFACE] start can only come from the system, which
 * sends it for Always-on VPN. [VpnService.isAlwaysOn] is not a usable signal
 * here: during that very start it can still report false.
 */
internal fun classifyVpnServiceCommand(
    action: String?,
    alwaysOn: Boolean,
    sessionLive: Boolean,
): VpnServiceCommand = when (action) {
    TriVpnService.ACTION_START -> VpnServiceCommand.START
    TriVpnService.ACTION_STOP -> VpnServiceCommand.STOP
    TriVpnService.ACTION_RESTART -> VpnServiceCommand.RESTART
    VpnService.SERVICE_INTERFACE -> if (sessionLive) VpnServiceCommand.IGNORE else VpnServiceCommand.START
    // A null or unknown intent never brings a tunnel up unless Always-on is
    // confirmed for this app.
    else -> if (alwaysOn && !sessionLive) VpnServiceCommand.START else VpnServiceCommand.IGNORE
}
