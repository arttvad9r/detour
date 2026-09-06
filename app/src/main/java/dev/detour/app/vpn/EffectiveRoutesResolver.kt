package dev.detour.app.vpn

import android.content.pm.PackageManager
import android.os.Process
import dev.detour.app.core.AppRoute

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
        .associateWith { uid ->
            val visiblePackages = packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
            trustworthyUidPackages(visiblePackages, packageManager.getNameForUid(uid))
        }

    return ResolvedRouteSnapshot(
        effective = effectiveRoutes(
            routes,
            installed,
            uidPackages,
            excludedUids = setOf(Process.myUid()),
        ),
        installedUids = installed,
    )
}

fun resolveEffectiveRoutes(
    packageManager: PackageManager,
    routes: Map<String, AppRoute>,
): EffectiveRoutes = resolveRouteSnapshot(packageManager, routes).effective
