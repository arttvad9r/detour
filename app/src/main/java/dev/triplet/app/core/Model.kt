package dev.triplet.app.core

enum class AppRoute { DIRECT, VPN, DPI }

sealed interface VpnOutbound {
    data class Vless(val profile: VlessProfile) : VpnOutbound
    data class Warp(val profile: WarpProfile) : VpnOutbound
}

data class RoutingInput(
    val tunFd: Int,
    val apiLevel: Int,
    val vpn: VpnOutbound?,
    val vpnApps: Set<String>,
    val vpnUids: Map<String, Int>,
    val dpiApps: Set<String>,
    val nameserver: String = "8.8.8.8",
    val dpiPort: Int = 10808,
)
