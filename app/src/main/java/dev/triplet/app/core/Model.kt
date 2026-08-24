package dev.triplet.app.core

enum class AppRoute { DIRECT, VPN, DPI }
enum class Attribution { PROCESS_NAME, UID }

data class RoutingInput(
    val tunFd: Int,
    val apiLevel: Int,
    val ownPackage: String,
    val profile: VlessProfile?,
    val vpnApps: Set<String>,
    val vpnUids: Map<String, Int>,
    val dpiApps: Set<String>,
    val attribution: Attribution,
    val dpiPort: Int = 10808,
    val mixedPort: Int = 10809,
)
