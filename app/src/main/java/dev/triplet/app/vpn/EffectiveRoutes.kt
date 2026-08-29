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
    uidPackages: Map<Int, Set<String>>? = null,
    excludedUids: Set<Int> = emptySet(),
): EffectiveRoutes {
    // A VPN implementation must never route its own process UID back into its
    // TUN. Excluding by UID (not just package name) also covers shared-UID apps.
    val present = routes.keys.filter { pkg ->
        val uid = installedUids[pkg]
        uid != null && uid !in excludedUids
    }
    val byUid = present.groupBy { installedUids.getValue(it)!! }
    val conflicts = byUid.filter { (uid, packages) ->
        val siblings = uidPackages?.get(uid)
        if (uidPackages != null && siblings.isNullOrEmpty()) {
            true
        } else {
            (siblings ?: packages.toSet())
                .map { routes[it] ?: AppRoute.DIRECT }
                .distinct()
                .size > 1
        }
    }.keys
    if (conflicts.isNotEmpty()) return EffectiveRoutes(emptySet(), emptySet(), conflicts)
    return EffectiveRoutes(
        vpnPackages = present.filter { routes[it] == AppRoute.VPN }.toSet(),
        dpiPackages = present.filter { routes[it] == AppRoute.DPI }.toSet(),
    )
}
