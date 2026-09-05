package dev.triplet.app.core

enum class AppRoute { DIRECT, VPN, DPI }

sealed interface VpnOutbound {
    data class Vless(val profile: VlessProfile) : VpnOutbound
    data class Subscription(
        val url: String,
        val selectedNode: String? = null,
        val selectionMode: SubscriptionSelectionMode = SubscriptionSelectionMode.MANUAL,
    ) : VpnOutbound {
        init {
            require(url.isNotBlank())
            selectedNode?.let { node ->
                require(node.isNotBlank() && node == node.trim() && node.length <= 256)
                require(node.none { it.code < 0x20 || it.code == 0x7f })
            }
        }
    }
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
    val probeCredentials: ProbeCredentials = ProbeAuth.current(),
)
