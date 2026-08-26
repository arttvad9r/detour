package dev.triplet.app.vpn

import dev.triplet.app.core.AppRoute

data class EffectiveRoutes(
    val vpnPackages: Set<String>,
    val dpiPackages: Set<String>,
    val sharedUidConflict: Set<Int> = emptySet(),
) {
    val packages: Set<String> get() = vpnPackages + dpiPackages
    val isEmpty: Boolean get() = packages.isEmpty()
}

/** Filters persisted package names before any TUN/engine side effect. */
fun effectiveRoutes(
    routes: Map<String, AppRoute>,
    installedUids: Map<String, Int?>,
): EffectiveRoutes {
    val present = routes.keys.filter { installedUids[it] != null }
    val byUid = present.groupBy { installedUids.getValue(it)!! }
    val conflicts = byUid.filter { (_, packages) ->
        packages.map { routes.getValue(it) }.distinct().size > 1
    }.keys
    if (conflicts.isNotEmpty()) return EffectiveRoutes(emptySet(), emptySet(), conflicts)
    return EffectiveRoutes(
        vpnPackages = present.filter { routes[it] == AppRoute.VPN }.toSet(),
        dpiPackages = present.filter { routes[it] == AppRoute.DPI }.toSet(),
    )
}
