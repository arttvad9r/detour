package dev.triplet.app.vpn

import android.content.pm.PackageManager
import dev.triplet.app.core.AppRoute

data class ResolvedRouteSnapshot(
    val effective: EffectiveRoutes,
    val installedUids: Map<String, Int?>,
)

/** Resolve persisted package routes against one PackageManager ownership snapshot. */
fun resolveRouteSnapshot(
    packageManager: PackageManager,
    routes: Map<String, AppRoute>,
): ResolvedRouteSnapshot {
    if (routes.isEmpty()) {
        return ResolvedRouteSnapshot(EffectiveRoutes(emptySet(), emptySet()), emptyMap())
    }

    val installed = routes.keys.associateWith { pkg ->
        runCatching { packageManager.getPackageUid(pkg, 0) }.getOrNull()
    }
    val uidPackages = installed.values
        .filterNotNull()
        .distinct()
        .associateWith { uid -> packageManager.getPackagesForUid(uid)?.toSet().orEmpty() }

    return ResolvedRouteSnapshot(
        effective = effectiveRoutes(routes, installed, uidPackages),
        installedUids = installed,
    )
}

fun resolveEffectiveRoutes(
    packageManager: PackageManager,
    routes: Map<String, AppRoute>,
): EffectiveRoutes = resolveRouteSnapshot(packageManager, routes).effective
