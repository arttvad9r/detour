package dev.triplet.app.vpn

import android.content.pm.PackageManager
import dev.triplet.app.core.AppRoute

/** Resolve persisted package routes against the packages/UIDs visible to Android. */
fun resolveEffectiveRoutes(
    packageManager: PackageManager,
    routes: Map<String, AppRoute>,
): EffectiveRoutes {
    if (routes.isEmpty()) return EffectiveRoutes(emptySet(), emptySet())

    val installed = routes.keys.associateWith { pkg ->
        runCatching { packageManager.getPackageUid(pkg, 0) }.getOrNull()
    }
    val uidPackages = installed.values
        .filterNotNull()
        .distinct()
        .associateWith { uid -> packageManager.getPackagesForUid(uid)?.toSet().orEmpty() }

    return effectiveRoutes(routes, installed, uidPackages)
}
