package dev.triplet.app.vpn

internal fun shouldAttemptForegroundAutoConnect(
    hasInternet: Boolean,
    validated: Boolean,
    vpnTransport: Boolean,
): Boolean = hasInternet && validated && !vpnTransport
