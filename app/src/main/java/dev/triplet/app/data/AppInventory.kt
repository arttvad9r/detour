package dev.triplet.app.data

import android.content.Context
import android.content.pm.PackageManager

object AppInventory {
    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val self = context.packageName
        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { it.packageName != self }
            .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
            .toList()
    }
}
