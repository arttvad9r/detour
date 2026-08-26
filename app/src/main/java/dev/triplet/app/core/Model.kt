package dev.triplet.app.core

enum class AppRoute { DIRECT, VPN, DPI }

data class RoutingInput(
    val tunFd: Int,
    val apiLevel: Int,
    val profile: VlessProfile?,
    val vpnApps: Set<String>,
    val vpnUids: Map<String, Int>,
    val dpiApps: Set<String>,
    val nameserver: String = "8.8.8.8",
    val dpiPort: Int = 10808,
    val probeUsername: String = "",
    val probePassword: String = "",
)
