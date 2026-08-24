package dev.triplet.app.data

import android.content.Context
import android.content.Intent

object AppInventory {
    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val self = context.packageName
        // getLaunchIntentForPackage needs CATEGORY_DEFAULT, which some launchable
        // browsers (e.g. com.artt.minibrowser) omit — match MAIN/LAUNCHER directly.
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { pm.queryIntentActivities(launcher.setPackage(it.packageName), 0).isNotEmpty() }
            .filter { it.packageName != self }
            .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
            .toList()
    }
}
