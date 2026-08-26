package dev.triplet.app.data

import dev.triplet.app.core.AppRoute
import java.text.Collator

object AppRouteOrdering {
    fun snapshot(apps: List<AppInfo>, routes: Map<String, AppRoute>): List<String> {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        return apps.sortedWith(
            Comparator { a, b ->
                val configured = (routes[a.packageName] ?: AppRoute.DIRECT) != AppRoute.DIRECT
                val otherConfigured = (routes[b.packageName] ?: AppRoute.DIRECT) != AppRoute.DIRECT
                when {
                    configured != otherConfigured -> if (configured) -1 else 1
                    else -> collator.compare(a.label, b.label)
                }
            },
        ).map(AppInfo::packageName)
    }

    fun apply(apps: List<AppInfo>, order: List<String>): List<AppInfo> {
        val byId = apps.associateBy(AppInfo::packageName)
        return order.mapNotNull(byId::get)
    }
}
